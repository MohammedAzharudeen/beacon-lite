package com.beacon.chat;

import com.beacon.chat.tools.ToolContext;
import com.beacon.common.Metrics;
import com.beacon.config.BeaconProperties;
import com.beacon.insight.ReportService;
import com.beacon.insight.report.InsightReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ask Beacon: scope check → model with tools (max 4 calls) → number check → answer, with a
 * rule-based fallback whenever the model is off, slow or invents a number.
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final int MAX_HISTORY = 20;
  private static final int MAX_HISTORY_CHARS = 4000;

  private final ReportService reports;
  private final ScopeGuard scope;
  private final LlmProvider llm;
  private final ToolRegistry tools;
  private final NumberValidator validator;
  private final RuleBasedRouter router;
  private final PromptLoader prompts;
  private final Metrics metrics;
  private final int maxToolCalls;

  public ChatService(
      ReportService reports,
      ScopeGuard scope,
      LlmProvider llm,
      ToolRegistry tools,
      NumberValidator validator,
      RuleBasedRouter router,
      PromptLoader prompts,
      Metrics metrics,
      BeaconProperties properties) {
    this.reports = reports;
    this.scope = scope;
    this.llm = llm;
    this.tools = tools;
    this.validator = validator;
    this.router = router;
    this.prompts = prompts;
    this.metrics = metrics;
    this.maxToolCalls = properties.llm().maxToolCalls();
  }

  /** A prior turn of the conversation. */
  public record Turn(String role, String content) {}

  public ChatResponse ask(long storeId, String message, List<Turn> history) {
    InsightReport report = reports.require(storeId);
    ToolContext context = new ToolContext(storeId, report);
    Optional<String> refusal = scope.check(message);
    if (refusal.isPresent()) {
      metrics.increment("chat.requests.scope");
      log.info("[CHAT] store={} scope refusal", storeId);
      return new ChatResponse(
          refusal.get(),
          List.of(),
          0,
          false,
          AnswerConfidence.NOT_AVAILABLE,
          ChatProvider.SCOPE,
          report.snapshotId());
    }
    if (!llm.isReachable()) {
      return rules(message, context);
    }
    try {
      List<ChatMessage> conversation = new ArrayList<>();
      conversation.add(ChatMessage.system(prompts.systemPrompt()));
      history.stream()
          .skip(Math.max(0, history.size() - MAX_HISTORY))
          .filter(t -> "user".equals(t.role()) || "assistant".equals(t.role()))
          .forEach(
              t -> conversation.add(new ChatMessage(t.role(), cut(t.content()), null, List.of())));
      conversation.add(ChatMessage.user(message));
      List<ToolResult> results = new ArrayList<>();
      for (int call = 0; call <= maxToolCalls; call++) {
        LlmReply reply = llm.complete(conversation, tools.definitions());
        if (!reply.hasToolCalls()) {
          if (results.isEmpty()) {
            // The model answered without data; never show an unsupported answer
            return rules(message, context, ChatProvider.LLM, true);
          }
          return verified(reply.text(), results, report);
        }
        if (call == maxToolCalls) {
          break;
        }
        conversation.add(new ChatMessage("assistant", reply.text(), null, reply.toolCalls()));
        for (ToolCall tc : reply.toolCalls()) {
          ToolResult result = tools.run(tc.name(), tc.arguments(), context);
          results.add(result);
          conversation.add(ChatMessage.toolResult(tc.id(), result.data().toString()));
        }
      }
      return fallbackFrom(results, report);
    } catch (LlmUnavailableException e) {
      log.warn("[CHAT] store={} llm fallback reason={}", storeId, e.getMessage());
      return rules(message, context);
    }
  }

  private ChatResponse verified(String draft, List<ToolResult> results, InsightReport report) {
    NumberValidator.Validation v = validator.check(draft, results);
    metrics.increment("chat.requests.llm");
    if (v.ok()) {
      return new ChatResponse(
          draft.trim(),
          names(results),
          v.verified(),
          false,
          confidence(results),
          ChatProvider.LLM,
          report.snapshotId());
    }
    metrics.increment("chat.validator_failures");
    log.warn("[CHAT] store={} unverified numbers={}", report.storeId(), v.unverified());
    return fallbackFrom(results, report);
  }

  private ChatResponse fallbackFrom(List<ToolResult> results, InsightReport report) {
    ToolResult last = results.isEmpty() ? null : results.get(results.size() - 1);
    if (last == null) {
      return new ChatResponse(
          "I couldn't find data for that question.",
          List.of(),
          0,
          true,
          AnswerConfidence.NOT_AVAILABLE,
          ChatProvider.LLM,
          report.snapshotId());
    }
    String answer = router.answer(last);
    NumberValidator.Validation v = validator.check(answer, List.of(last));
    return new ChatResponse(
        answer,
        names(results),
        v.verified(),
        true,
        confidence(results),
        ChatProvider.LLM,
        report.snapshotId());
  }

  private ChatResponse rules(String message, ToolContext context) {
    return rules(message, context, ChatProvider.RULES, false);
  }

  private ChatResponse rules(
      String message, ToolContext context, ChatProvider provider, boolean fallback) {
    metrics.increment("chat.requests.rules");
    RuleBasedRouter.Route route = router.route(message);
    ToolResult result = tools.run(route.tool(), route.arguments(), context);
    String answer = router.answer(result);
    NumberValidator.Validation v = validator.check(answer, List.of(result));
    return new ChatResponse(
        answer,
        List.of(route.tool()),
        v.verified(),
        fallback,
        confidence(List.of(result)),
        provider,
        context.report().snapshotId());
  }

  private static AnswerConfidence confidence(List<ToolResult> results) {
    for (ToolResult r : results) {
      String json = r.data().toString();
      if (json.contains("\"estimate\":true") || json.contains("estimatedAtRiskPerWeek")) {
        return AnswerConfidence.ESTIMATE;
      }
      if (json.contains("\"status\":\"NOT_AVAILABLE\"")
          && results.size() == 1
          && r.toolName().equals("get_recent_changes")) {
        return AnswerConfidence.NOT_AVAILABLE;
      }
    }
    return AnswerConfidence.HIGH;
  }

  private static List<String> names(List<ToolResult> results) {
    return results.stream().map(ToolResult::toolName).distinct().toList();
  }

  private static String cut(String text) {
    if (text == null) {
      return "";
    }
    return text.length() <= MAX_HISTORY_CHARS ? text : text.substring(0, MAX_HISTORY_CHARS);
  }
}

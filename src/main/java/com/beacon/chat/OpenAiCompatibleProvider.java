package com.beacon.chat;

import com.beacon.common.Metrics;
import com.beacon.config.BeaconProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calls an OpenAI-format {@code /chat/completions} endpoint with tools. Default: local Ollama with
 * Qwen2.5 7B. The API key, when set, is sent as a bearer token and never logged.
 */
@Component
public class OpenAiCompatibleProvider implements LlmProvider {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
  private static final Duration REACHABILITY_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration REACHABILITY_CACHE = Duration.ofSeconds(30);

  private final BeaconProperties.Llm config;
  private final ObjectMapper mapper;
  private final Metrics metrics;
  private final HttpClient http;
  private volatile long reachableCheckedAt;
  private volatile boolean reachable;

  public OpenAiCompatibleProvider(
      BeaconProperties properties, ObjectMapper mapper, Metrics metrics) {
    this.config = properties.llm();
    this.mapper = mapper;
    this.metrics = metrics;
    this.http = HttpClient.newBuilder().connectTimeout(REACHABILITY_TIMEOUT).build();
  }

  @Override
  public LlmReply complete(List<ChatMessage> messages, List<JsonNode> tools) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", config.model());
    body.put("temperature", 0);
    body.put("stream", false);
    ArrayNode msgs = body.putArray("messages");
    for (ChatMessage m : messages) {
      ObjectNode node = msgs.addObject();
      node.put("role", m.role());
      node.put("content", m.content() == null ? "" : m.content());
      if (m.toolCallId() != null) {
        node.put("tool_call_id", m.toolCallId());
      }
      if (!m.toolCalls().isEmpty()) {
        ArrayNode calls = node.putArray("tool_calls");
        for (ToolCall c : m.toolCalls()) {
          ObjectNode call = calls.addObject();
          call.put("id", c.id());
          call.put("type", "function");
          call.putObject("function").put("name", c.name()).put("arguments", c.arguments());
        }
      }
    }
    ArrayNode toolArray = body.putArray("tools");
    for (JsonNode t : tools) {
      ObjectNode tool = toolArray.addObject();
      tool.put("type", "function");
      tool.set("function", t);
    }
    long started = System.nanoTime();
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(config.baseUrl() + "/chat/completions"))
              .timeout(config.timeout())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      if (!config.apiKey().isBlank()) {
        request.header("Authorization", "Bearer " + config.apiKey());
      }
      HttpResponse<String> response =
          http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      long ms = (System.nanoTime() - started) / 1_000_000;
      metrics.record("chat.llm_latency_ms", ms);
      if (response.statusCode() != 200) {
        throw new LlmUnavailableException("Model returned HTTP " + response.statusCode(), null);
      }
      JsonNode message = mapper.readTree(response.body()).path("choices").path(0).path("message");
      List<ToolCall> calls = new ArrayList<>();
      for (JsonNode c : message.path("tool_calls")) {
        JsonNode fn = c.path("function");
        JsonNode args = fn.path("arguments");
        calls.add(
            new ToolCall(
                c.path("id").asText("call_" + calls.size()),
                fn.path("name").asText(),
                args.isTextual() ? args.asText() : args.toString()));
      }
      log.info("[LLM] model={} ms={} toolCalls={}", config.model(), ms, calls.size());
      return new LlmReply(message.path("content").asText(""), calls);
    } catch (IOException e) {
      markUnreachable();
      throw new LlmUnavailableException("Model not reachable: " + e.getClass().getSimpleName(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmUnavailableException("Interrupted", e);
    }
  }

  @Override
  public boolean isReachable() {
    long now = System.nanoTime();
    if (now - reachableCheckedAt < REACHABILITY_CACHE.toNanos() && reachableCheckedAt != 0) {
      return reachable;
    }
    boolean ok;
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(config.baseUrl() + "/models"))
              .timeout(REACHABILITY_TIMEOUT)
              .GET();
      if (!config.apiKey().isBlank()) {
        request.header("Authorization", "Bearer " + config.apiKey());
      }
      ok = http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (IOException | IllegalArgumentException e) {
      ok = false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      ok = false;
    }
    reachable = ok;
    reachableCheckedAt = now;
    return ok;
  }

  private void markUnreachable() {
    reachable = false;
    reachableCheckedAt = System.nanoTime();
  }

  @Override
  public String provider() {
    return config.provider();
  }

  @Override
  public String model() {
    return config.model();
  }

  @Override
  public String baseUrl() {
    return config.baseUrl();
  }
}

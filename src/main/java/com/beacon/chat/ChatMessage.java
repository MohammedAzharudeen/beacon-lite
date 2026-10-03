package com.beacon.chat;

import java.util.List;

/**
 * One message in an OpenAI-format conversation.
 *
 * @param role system, user, assistant or tool
 * @param toolCallId set on tool results
 * @param toolCalls set when the assistant asks for tools
 */
public record ChatMessage(
    String role, String content, String toolCallId, List<ToolCall> toolCalls) {

  public ChatMessage {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public static ChatMessage system(String content) {
    return new ChatMessage("system", content, null, List.of());
  }

  public static ChatMessage user(String content) {
    return new ChatMessage("user", content, null, List.of());
  }

  public static ChatMessage assistant(String content) {
    return new ChatMessage("assistant", content, null, List.of());
  }

  public static ChatMessage toolResult(String toolCallId, String json) {
    return new ChatMessage("tool", json, toolCallId, List.of());
  }
}

package com.beacon.chat;

import java.util.List;

/** The model's reply: either text, or tool calls to run first. */
public record LlmReply(String text, List<ToolCall> toolCalls) {

  public LlmReply {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}

package com.beacon.chat;

import java.util.List;

/**
 * An Ask Beacon answer.
 *
 * @param numbersVerified numbers in the answer found in tool results
 * @param validatorFallback true when the model's draft failed the number check and a safe template
 *     answer was used instead
 */
public record ChatResponse(
    String answer,
    List<String> toolsUsed,
    int numbersVerified,
    boolean validatorFallback,
    AnswerConfidence confidence,
    ChatProvider provider,
    Long snapshotId) {

  public ChatResponse {
    toolsUsed = List.copyOf(toolsUsed);
  }
}

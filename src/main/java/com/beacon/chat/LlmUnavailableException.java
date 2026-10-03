package com.beacon.chat;

/**
 * The model couldn't be reached or didn't answer in time; the rule-based router answers instead.
 */
public class LlmUnavailableException extends RuntimeException {

  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

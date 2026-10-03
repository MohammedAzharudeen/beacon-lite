package com.beacon.common;

import java.util.Map;

/** The single exception type for expected failures; carries a code, a message and a hint. */
public class BeaconException extends RuntimeException {

  private final ErrorCode code;
  private final String hint;
  private final Map<String, Object> details;

  public BeaconException(ErrorCode code) {
    this(code, code.defaultMessage(), code.defaultHint(), Map.of(), null);
  }

  public BeaconException(ErrorCode code, Map<String, Object> details) {
    this(code, code.defaultMessage(), code.defaultHint(), details, null);
  }

  public BeaconException(ErrorCode code, String message, String hint, Throwable cause) {
    this(code, message, hint, Map.of(), cause);
  }

  public BeaconException(
      ErrorCode code, String message, String hint, Map<String, Object> details, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.hint = hint;
    this.details = Map.copyOf(details);
  }

  public ErrorCode code() {
    return code;
  }

  public String hint() {
    return hint;
  }

  public Map<String, Object> details() {
    return details;
  }
}

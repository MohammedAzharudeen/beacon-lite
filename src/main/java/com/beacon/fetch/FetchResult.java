package com.beacon.fetch;

import java.net.URI;

/**
 * Result of one polite fetch.
 *
 * @param outcome what happened
 * @param uri the URL finally fetched (after redirects)
 * @param status HTTP status, or 0 when no response was received
 * @param contentType response Content-Type, or {@code null}
 * @param body response body as text, or {@code null} when not received
 * @param bytes response size in bytes
 * @param durationMs time taken
 * @param robotsRule the robots.txt rule that blocked the request, when blocked
 */
public record FetchResult(
    FetchOutcome outcome,
    URI uri,
    int status,
    String contentType,
    String body,
    long bytes,
    long durationMs,
    String robotsRule) {

  static FetchResult blocked(URI uri, String rule) {
    return new FetchResult(FetchOutcome.BLOCKED_BY_ROBOTS, uri, 0, null, null, 0, 0, rule);
  }

  static FetchResult tooLarge(URI uri, int status, long bytes, long durationMs) {
    return new FetchResult(
        FetchOutcome.TOO_LARGE, uri, status, null, null, bytes, durationMs, null);
  }

  public boolean isOk() {
    return outcome == FetchOutcome.RESPONSE && status >= 200 && status < 300;
  }

  /** True when the response says it is JSON (status alone is not proof: some sites return HTML). */
  public boolean isJson() {
    return contentType != null && contentType.toLowerCase().contains("json");
  }
}

package com.beacon.robots;

/**
 * Whether a path may be fetched, and the rule that decided it (shown as evidence in reports).
 *
 * @param allowed true if the path may be fetched
 * @param rule the matching rule, e.g. {@code "Disallow: /policies/"}, or {@code null} if none
 */
public record RobotsDecision(boolean allowed, String rule) {

  static RobotsDecision allowedByDefault() {
    return new RobotsDecision(true, null);
  }

  public boolean disallowed() {
    return !allowed;
  }
}

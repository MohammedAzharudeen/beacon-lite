package com.beacon.journey;

/**
 * One journey check result.
 *
 * @param score 0–100, or {@code null} when the check didn't run
 * @param fix suggested fix, or {@code null} when nothing needs fixing
 * @param reason why the check didn't run (robots rule, page missing…), or {@code null}
 */
public record JourneyCheck(
    String key,
    String label,
    CheckStatus status,
    Integer score,
    String evidence,
    String fix,
    String reason) {

  public boolean ran() {
    return score != null
        && (status == CheckStatus.CHECKED || status == CheckStatus.CHECKED_VIA_ALTERNATIVE);
  }
}

package com.beacon.journey;

import java.util.List;

/**
 * One stage of the shopper journey.
 *
 * @param score mean of the checks that ran, or {@code null} when none ran
 * @param checksRun checks that produced a score (coverage)
 */
public record JourneyStage(
    JourneyStageKey stage,
    String label,
    Integer score,
    int checksRun,
    int checksTotal,
    List<JourneyCheck> checks) {

  public JourneyStage {
    checks = List.copyOf(checks);
  }
}

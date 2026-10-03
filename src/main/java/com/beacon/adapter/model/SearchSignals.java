package com.beacon.adapter.model;

import java.util.List;

/**
 * Results of the store search test (only where robots.txt allows search).
 *
 * @param robotsRule the rule that blocked search, when blocked
 */
public record SearchSignals(SourceStatus status, String robotsRule, List<SearchProbe> probes) {

  public SearchSignals {
    probes = List.copyOf(probes);
  }

  /**
   * One search term and what came back.
   *
   * @param resultCount products returned
   * @param relevantCount results whose title or type contains the term
   * @param topTitles first few result titles, as evidence
   */
  public record SearchProbe(
      String term, int resultCount, int relevantCount, List<String> topTitles) {

    public SearchProbe {
      topTitles = List.copyOf(topTitles);
    }
  }
}

package com.beacon.web.dto;

import java.time.Instant;
import java.util.List;

/** KPI values per snapshot, oldest first (trend lines). */
public record TrendsResponse(List<Point> points) {

  public record Point(
      long snapshotId,
      Instant at,
      double sizesSoldOutPct,
      Integer journeyScore,
      String atRiskPerWeek,
      int soldOutVariants) {}
}

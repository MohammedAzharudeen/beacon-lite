package com.beacon.insight;

import com.beacon.insight.report.ChangeCounts;
import java.util.Set;

/**
 * What changed between the previous and the latest snapshot (from change events).
 *
 * @param counts {@code null} on the first snapshot
 * @param soldOutVariantIds external ids of variants that sold out in the latest window
 */
public record ChangeContext(ChangeCounts counts, Set<Long> soldOutVariantIds) {

  public ChangeContext {
    soldOutVariantIds = Set.copyOf(soldOutVariantIds);
  }

  public static ChangeContext firstSnapshot() {
    return new ChangeContext(null, Set.of());
  }

  public boolean hasPrevious() {
    return counts != null;
  }
}

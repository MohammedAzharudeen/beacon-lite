package com.beacon.insight.report;

import java.util.List;

/**
 * A product with sold-out sizes.
 *
 * @param coreRange e.g. "7–9.5"; {@code null} when the sizes have no core range
 * @param missingCore core sizes sold out
 * @param onlyOneLeft true when exactly one size is still available
 */
public record SizeGapRow(
    long productId,
    String title,
    String productType,
    String imageUrl,
    String productUrl,
    List<SizeCell> sizes,
    String coreRange,
    int missingCore,
    int soldOutSizes,
    int totalSizes,
    boolean onlyOneLeft) {

  public SizeGapRow {
    sizes = List.copyOf(sizes);
  }
}

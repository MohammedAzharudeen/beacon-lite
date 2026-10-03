package com.beacon.insight.report;

/**
 * Size-analysis totals.
 *
 * @param productsMissingCoreSizes products (not excluded) with a core size sold out while another
 *     size is still available
 * @param onlyOneSizeLeft products with three or more sizes and exactly one still available
 * @param fullySoldOut products (not excluded) with every size sold out
 * @param noSizeOption products without a size option (skipped)
 * @param unrecognised products whose size values weren't recognised (listed, not guessed)
 */
public record SizeSummary(
    int productsAnalysed,
    int productsMissingCoreSizes,
    int onlyOneSizeLeft,
    int fullySoldOut,
    int noSizeOption,
    int unrecognised) {}

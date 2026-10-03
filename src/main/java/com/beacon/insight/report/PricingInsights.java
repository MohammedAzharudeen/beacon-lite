package com.beacon.insight.report;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Pricing findings.
 *
 * @param compareAtEqualsPrice products with a compare-at price equal to the selling price (catalog
 *     clean-up; whether a strike-through shows depends on the theme, so no claim is made)
 * @param discountBuckets products by discount depth, e.g. "10–30%" → 120
 * @param freeShipping {@code null} fields mean "Not found"
 */
public record PricingInsights(
    int compareAtEqualsPrice,
    int discounted,
    Map<String, Integer> discountBuckets,
    int deepDiscount,
    int deepDiscountPercent,
    int deadStock,
    FreeShipping freeShipping) {

  public PricingInsights {
    discountBuckets = Map.copyOf(discountBuckets);
  }

  /**
   * Free-shipping offer vs typical price.
   *
   * @param threshold order value for free shipping, or {@code null} when no amount was found
   * @param medianPrice median product price
   * @param ratio threshold ÷ median price, or {@code null}
   */
  public record FreeShipping(
      String text, BigDecimal threshold, BigDecimal medianPrice, Double ratio, String source) {}
}

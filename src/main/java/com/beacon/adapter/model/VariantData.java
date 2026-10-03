package com.beacon.adapter.model;

import java.math.BigDecimal;

/**
 * One purchasable version of a product as published by the store.
 *
 * @param available the store's public in-stock flag
 * @param compareAtPrice "was" price, or {@code null}
 */
public record VariantData(
    long externalId,
    String title,
    String option1,
    String option2,
    String option3,
    String sku,
    boolean available,
    BigDecimal price,
    BigDecimal compareAtPrice) {

  /** Value of option 1, 2 or 3. */
  public String option(int position) {
    return switch (position) {
      case 1 -> option1;
      case 2 -> option2;
      case 3 -> option3;
      default -> null;
    };
  }
}

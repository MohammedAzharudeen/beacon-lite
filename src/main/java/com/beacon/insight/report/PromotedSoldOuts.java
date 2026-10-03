package com.beacon.insight.report;

import java.util.List;

/**
 * Fully sold-out products the store still promotes.
 *
 * @param promotedChecked products in the promoted set
 * @param notice set when home-page links load via JavaScript and only collections were checked
 */
public record PromotedSoldOuts(List<ProductRef> products, int promotedChecked, String notice) {

  public PromotedSoldOuts {
    products = List.copyOf(products);
  }
}

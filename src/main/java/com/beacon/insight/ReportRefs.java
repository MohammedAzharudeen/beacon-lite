package com.beacon.insight;

import com.beacon.insight.report.ProductRef;

/** Builds product references for reports. */
final class ReportRefs {

  private ReportRefs() {}

  static ProductRef ref(ProductView view) {
    return new ProductRef(
        view.product().externalId(),
        view.product().title(),
        view.product().productType(),
        view.product().imageUrls().isEmpty() ? null : view.product().imageUrls().get(0),
        view.productUrl());
  }
}

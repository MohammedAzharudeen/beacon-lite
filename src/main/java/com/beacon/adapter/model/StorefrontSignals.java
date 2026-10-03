package com.beacon.adapter.model;

import java.util.List;

/** Everything read from storefront pages besides the catalog. */
public record StorefrontSignals(
    HomePageSignals home,
    PolicySignals returns,
    ShippingSignals shipping,
    SearchSignals search,
    List<ProductPageSignals> productPages,
    AltTextSample altText) {

  public StorefrontSignals {
    productPages = List.copyOf(productPages);
  }
}

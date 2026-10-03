package com.beacon.adapter.model;

import java.util.Set;

/**
 * What a sampled sold-out product page shows.
 *
 * @param notifyMeText "notify me"-style text present in the HTML
 * @param sizeGuide size guide or size chart mentioned
 * @param apps storefront apps detected on the page
 */
public record ProductPageSignals(
    String handle,
    SourceStatus status,
    long bytes,
    boolean notifyMeText,
    boolean sizeGuide,
    Set<String> apps) {

  public ProductPageSignals {
    apps = Set.copyOf(apps);
  }
}

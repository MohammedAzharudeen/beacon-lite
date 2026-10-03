package com.beacon.adapter.model;

import java.util.List;
import java.util.Set;

/**
 * What the home page HTML shows.
 *
 * @param bytes page size
 * @param scriptCount number of script tags
 * @param productHandles products linked from the page (JavaScript-rendered links are not seen)
 * @param collectionHandles collections linked from the page
 * @param footerPages {@code /pages/*} links matching returns or shipping topics
 * @param apps storefront apps detected (keys of {@code detection.apps})
 * @param notifyMeText true when "notify me"-style text appears in the HTML
 * @param siteName the store's name from {@code og:site_name}, or {@code null}
 */
public record HomePageSignals(
    SourceStatus status,
    String robotsRule,
    long bytes,
    int scriptCount,
    List<String> productHandles,
    List<String> collectionHandles,
    List<String> footerPages,
    Set<String> apps,
    boolean notifyMeText,
    String siteName) {

  public HomePageSignals {
    productHandles = List.copyOf(productHandles);
    collectionHandles = List.copyOf(collectionHandles);
    footerPages = List.copyOf(footerPages);
    apps = Set.copyOf(apps);
  }

  public static HomePageSignals unavailable(SourceStatus status, String robotsRule) {
    return new HomePageSignals(
        status, robotsRule, 0, 0, List.of(), List.of(), List.of(), Set.of(), false, null);
  }
}

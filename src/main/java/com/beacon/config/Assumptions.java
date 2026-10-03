package com.beacon.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Every judgment call, loaded from {@code config/assumptions.yml}. Field names mirror the YAML
 * keys.
 *
 * @param version SHA-256 of the file; stored with every report
 */
public record Assumptions(
    String version,
    CoreSizes coreSizes,
    Exclusions exclusions,
    String bestSellerCollectionPattern,
    Demand demand,
    GapWeights gapWeights,
    Samples samples,
    Search search,
    Catalog catalog,
    Pricing pricing,
    Signals signals,
    JourneyThresholds journeyThresholds,
    Detection detection) {

  public Assumptions withVersion(String newVersion) {
    return new Assumptions(
        newVersion,
        coreSizes,
        exclusions,
        bestSellerCollectionPattern,
        demand,
        gapWeights,
        samples,
        search,
        catalog,
        pricing,
        signals,
        journeyThresholds,
        detection);
  }

  public record CoreSizes(String rule, int percent) {}

  public record Exclusions(
      List<String> preOrderTags,
      List<String> backOrderTags,
      List<String> nonPhysicalTypes,
      List<String> bundleTypes,
      LikelyDiscontinued likelyDiscontinued) {}

  public record LikelyDiscontinued(
      boolean fullySoldOut, boolean notInBestSellerCollection, boolean wasDiscounted) {}

  public record Demand(
      int weeklyDemandBaselineUnits,
      SignalWeights signalWeights,
      int recentlyLaunchedDays,
      ConfidenceThresholds confidence) {}

  public record SignalWeights(
      double bestSellerCollection,
      double promoted,
      double sellOutSpeed,
      double fullPrice,
      double recentlyLaunched) {}

  public record ConfidenceThresholds(int high, int medium) {}

  public record GapWeights(double core, double edge) {}

  public record Samples(
      int soldOutProductPages,
      int altTextProducts,
      int featuredCollections,
      int featuredCollectionPages,
      int footerPages,
      int genericProductPages) {}

  public record Search(String termSource, int termCount) {}

  public record Catalog(int minImages, int thinDescriptionChars, int thinTitleChars) {}

  public record Pricing(int deepDiscountPercent, DeadStock deadStock) {}

  public record DeadStock(int publishedOlderThanDays, int discountAtLeastPercent) {}

  public record Signals(int minHomeLinks) {}

  /** Linear band: {@code best} scores 100, {@code worst} scores 0. */
  public record Band(BigDecimal best, BigDecimal worst) {

    /** Scores a value on this band, clamped to 0–100 and rounded to a whole number. */
    public int score(double value) {
      double b = best.doubleValue();
      double w = worst.doubleValue();
      double ratio = (value - w) / (b - w);
      return (int) Math.round(Math.max(0, Math.min(1, ratio)) * 100);
    }
  }

  public record JourneyThresholds(
      Band homePageBytes,
      Band homePageScripts,
      Band variantsSoldOutPercent,
      Band coreSizeGapPercent,
      Band freeShippingToMedianPriceRatio,
      Band returnsWindowDays) {}

  public record Detection(
      String shopifyMarkers,
      String botProtection,
      Map<String, String> apps,
      List<String> wishlistApps,
      List<String> emailCaptureApps,
      List<String> bnplApps,
      String notifyMeText,
      String sizeGuideText,
      String returnsPages,
      String shippingPages) {}
}

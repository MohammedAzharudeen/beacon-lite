package com.beacon.insight.report;

import com.beacon.journey.JourneyStage;
import java.time.Instant;
import java.util.List;

/**
 * Everything the dashboard, CLI brief and chat show for one snapshot. Stored with the snapshot id
 * and assumptions version so every number can be traced and recomputed.
 *
 * @param actions every candidate action, best first; the top 5 is re-picked from it when the
 *     merchant changes an action's status
 * @param categories the store's product types, most common first (filters)
 * @param restockTotal size of the full restock list (the API may return only the first rows)
 * @param sizeGapTotal size of the full size-gap list
 */
public record InsightReport(
    long storeId,
    long snapshotId,
    String domain,
    String assumptionsVersion,
    Instant generatedAt,
    Instant capturedAt,
    String currency,
    CatalogSummary catalog,
    List<String> categories,
    Headline headline,
    Kpis kpis,
    List<Action> topActions,
    List<Action> actions,
    List<RestockRow> restock,
    int restockTotal,
    List<ExclusionCount> exclusions,
    List<ExcludedProduct> excludedProducts,
    List<SizeGapRow> sizeGaps,
    int sizeGapTotal,
    SizeSummary sizeSummary,
    PromotedSoldOuts promotedSoldOuts,
    CatalogQuality catalogQuality,
    PricingInsights pricing,
    List<JourneyStage> journey,
    Integer journeyScore,
    List<String> dataNotes) {

  public InsightReport {
    categories = List.copyOf(categories);
    topActions = List.copyOf(topActions);
    actions = List.copyOf(actions);
    restock = List.copyOf(restock);
    exclusions = List.copyOf(exclusions);
    excludedProducts = List.copyOf(excludedProducts);
    sizeGaps = List.copyOf(sizeGaps);
    journey = List.copyOf(journey);
    dataNotes = List.copyOf(dataNotes);
  }

  /** A copy with a different top-5 list (action statuses change without a rescan). */
  public InsightReport withTopActions(List<Action> newTopActions) {
    return new InsightReport(
        storeId,
        snapshotId,
        domain,
        assumptionsVersion,
        generatedAt,
        capturedAt,
        currency,
        catalog,
        categories,
        headline,
        kpis,
        newTopActions,
        actions,
        restock,
        restockTotal,
        exclusions,
        excludedProducts,
        sizeGaps,
        sizeGapTotal,
        sizeSummary,
        promotedSoldOuts,
        catalogQuality,
        pricing,
        journey,
        journeyScore,
        dataNotes);
  }

  /**
   * A lighter copy for the dashboard: the first rows of the long lists (full lists are paged
   * through their own endpoints) and no candidate list.
   */
  public InsightReport trimmed(int maxRows) {
    return new InsightReport(
        storeId,
        snapshotId,
        domain,
        assumptionsVersion,
        generatedAt,
        capturedAt,
        currency,
        catalog,
        categories,
        headline,
        kpis,
        topActions,
        List.of(),
        first(restock, maxRows),
        restockTotal,
        exclusions,
        first(excludedProducts, maxRows),
        first(sizeGaps, maxRows),
        sizeGapTotal,
        sizeSummary,
        promotedSoldOuts,
        catalogQuality,
        pricing,
        journey,
        journeyScore,
        dataNotes);
  }

  private static <T> List<T> first(List<T> list, int max) {
    return list.size() <= max ? list : list.subList(0, max);
  }
}

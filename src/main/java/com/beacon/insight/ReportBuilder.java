package com.beacon.insight;

import com.beacon.action.ActionState;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.RobotsInfo;
import com.beacon.common.Money;
import com.beacon.config.Assumptions;
import com.beacon.insight.report.Action;
import com.beacon.insight.report.CatalogQuality;
import com.beacon.insight.report.CatalogSummary;
import com.beacon.insight.report.EstimatedMoney;
import com.beacon.insight.report.ExcludedProduct;
import com.beacon.insight.report.ExclusionCount;
import com.beacon.insight.report.Headline;
import com.beacon.insight.report.InsightReport;
import com.beacon.insight.report.Kpis;
import com.beacon.insight.report.PricingInsights;
import com.beacon.insight.report.PromotedSoldOuts;
import com.beacon.insight.report.RestockRow;
import com.beacon.insight.report.SizeGapRow;
import com.beacon.insight.report.SizeSummary;
import com.beacon.intent.PublicStandInProvider;
import com.beacon.journey.JourneyCheck;
import com.beacon.journey.JourneyService;
import com.beacon.journey.JourneyStage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the insight report for one snapshot from pure rules: no network, no database. The same
 * snapshot, change data and assumptions always give the same report.
 */
public final class ReportBuilder {

  private static final int TOP_RESTOCK = 3;
  private static final int TOP_OTHER = 2;
  private static final int TOP_TOTAL = 5;
  private static final int MAX_EXCLUDED_LISTED = 500;

  private final Assumptions assumptions;

  public ReportBuilder(Assumptions assumptions) {
    this.assumptions = assumptions;
  }

  public InsightReport build(
      long storeId,
      long snapshotId,
      CatalogSnapshotData data,
      ChangeContext changes,
      Map<String, ActionState> actionStates,
      Instant generatedAt) {
    StoreView store =
        StoreView.build(data, assumptions, changes.soldOutVariantIds(), changes.hasPrevious());
    List<RestockRow> restock =
        new RestockService(assumptions, new PublicStandInProvider(assumptions.demand()))
            .rank(store);
    SizeGapService sizeGapService = new SizeGapService();
    List<SizeGapRow> gaps = sizeGapService.gaps(store);
    SizeSummary sizes = sizeGapService.summary(store);
    PromotedSoldOuts promoted = new PromotedService(assumptions.signals()).find(store);
    CatalogQuality catalog = new CatalogQualityService(assumptions.catalog()).assess(store);
    PricingInsights pricing = new PricingService(assumptions.pricing()).assess(store);
    String currency = data.store().currency();

    int variants = data.variantCount();
    int soldOutVariants =
        (int)
            data.products().stream()
                .flatMap(p -> p.variants().stream())
                .filter(v -> !v.available())
                .count();
    double soldOutPct = variants == 0 ? 0 : Math.round(1000.0 * soldOutVariants / variants) / 10.0;
    BigDecimal atRisk =
        restock.stream()
            .map(r -> new BigDecimal(r.atRiskPerWeek().amount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    EstimatedMoney atRiskMoney = EstimatedMoney.of(new Money(atRisk, currency));
    Kpis preliminary = new Kpis(soldOutPct, soldOutVariants, atRiskMoney, null, changes.counts());
    List<JourneyStage> journey =
        new JourneyService(assumptions)
            .score(store, new JourneyService.Inputs(preliminary, sizes, catalog, pricing));
    Integer journeyScore = JourneyService.overall(journey);
    Kpis kpis = new Kpis(soldOutPct, soldOutVariants, atRiskMoney, journeyScore, changes.counts());

    List<Action> candidates =
        actionCandidates(restock, promoted, pricing, catalog, journey, data.productCount());
    return new InsightReport(
        storeId,
        snapshotId,
        data.store().domain(),
        assumptions.version(),
        generatedAt,
        data.capturedAt(),
        currency,
        new CatalogSummary(
            data.productCount(), variants, data.catalog().capped(), data.catalog().sampled()),
        categories(data),
        headline(sizes, atRiskMoney, restock, promoted, soldOutPct, soldOutVariants),
        kpis,
        topActions(candidates, actionStates),
        candidates,
        restock,
        restock.size(),
        exclusions(store),
        excludedProducts(store),
        gaps,
        gaps.size(),
        sizes,
        promoted,
        catalog,
        pricing,
        journey,
        journeyScore,
        dataNotes(data, changes, sizes));
  }

  /** Re-applies merchant action statuses to a stored report's candidate list. */
  public static List<Action> topActions(List<Action> candidates, Map<String, ActionState> states) {
    List<Action> restock = new ArrayList<>();
    List<Action> other = new ArrayList<>();
    for (Action a : candidates) {
      if (states.get(a.actionKey()) == ActionState.DISMISSED) {
        continue;
      }
      (a.category().equals("Restock") ? restock : other).add(a);
    }
    List<Action> picked = new ArrayList<>();
    picked.addAll(restock.subList(0, Math.min(TOP_RESTOCK, restock.size())));
    picked.addAll(other.subList(0, Math.min(TOP_OTHER, other.size())));
    // Fill remaining slots from whichever list still has items
    for (Action a : restock.subList(Math.min(TOP_RESTOCK, restock.size()), restock.size())) {
      if (picked.size() >= TOP_TOTAL) {
        break;
      }
      picked.add(a);
    }
    for (Action a : other.subList(Math.min(TOP_OTHER, other.size()), other.size())) {
      if (picked.size() >= TOP_TOTAL) {
        break;
      }
      picked.add(a);
    }
    List<Action> ranked = new ArrayList<>();
    for (Action a : picked) {
      ranked.add(
          a.withRankAndStatus(
              ranked.size() + 1, states.getOrDefault(a.actionKey(), ActionState.TODO)));
    }
    return ranked;
  }

  /**
   * Every possible action, best first: restock rows by $ at risk, then other findings by severity
   * The top 5 shown on the overview takes the first 3 restock items and the first 2 others.
   */
  static List<Action> actionCandidates(
      List<RestockRow> restock,
      PromotedSoldOuts promoted,
      PricingInsights pricing,
      CatalogQuality catalog,
      List<JourneyStage> journey,
      int productCount) {
    List<Action> out = new ArrayList<>();
    for (RestockRow r : restock.subList(0, Math.min(20, restock.size()))) {
      List<String> sold =
          r.sizes().stream().filter(c -> !c.available()).map(c -> c.label()).toList();
      out.add(
          new Action(
              "RESTOCK:product:" + r.productId(),
              0,
              "Restock " + r.title() + ", " + summarise(sold),
              (r.totalSizes() - r.soldOutSizes())
                  + " of "
                  + r.totalSizes()
                  + " sizes left · signals: "
                  + String.join(
                      ", ",
                      r.signals().stream()
                          .map(s -> s.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                          .toList())
                  + " · confidence "
                  + r.confidence(),
              "Restock",
              r.atRiskPerWeek(),
              ActionState.TODO));
    }
    List<ScoredAction> other = new ArrayList<>();
    if (!promoted.products().isEmpty()) {
      other.add(
          new ScoredAction(
              80,
              new Action(
                  "PROMOTED_SOLD_OUT",
                  0,
                  promoted.products().size() + " sold-out products are still promoted",
                  "Linked from the home page or in best-seller / featured collections; hide them or add a \"notify me\" button",
                  "Discovery",
                  null,
                  ActionState.TODO)));
    }
    if (pricing.compareAtEqualsPrice() > 0 && productCount > 0) {
      double share = 100.0 * pricing.compareAtEqualsPrice() / productCount;
      other.add(
          new ScoredAction(
              Math.min(70, share),
              new Action(
                  "PRICING:COMPARE_AT_EQUALS_PRICE",
                  0,
                  pricing.compareAtEqualsPrice()
                      + " products: compare-at price equals selling price",
                  "Catalog clean-up: remove or correct compare-at prices that match the selling price",
                  "Catalog",
                  null,
                  ActionState.TODO)));
    }
    if (catalog.fewImages() > 0 && productCount > 0) {
      double share = 100.0 * catalog.fewImages() / productCount;
      other.add(
          new ScoredAction(
              share,
              new Action(
                  "CATALOG:FEW_IMAGES",
                  0,
                  catalog.fewImages()
                      + " products have fewer than "
                      + catalog.minImages()
                      + " images",
                  "Add photos to these products",
                  "Catalog",
                  null,
                  ActionState.TODO)));
    }
    for (JourneyStage stage : journey) {
      for (JourneyCheck check : stage.checks()) {
        if (check.ran()
            && check.fix() != null
            && !check.key().equals("VARIANTS_SOLD_OUT")
            && !check.key().equals("CORE_SIZE_GAPS")) {
          other.add(
              new ScoredAction(
                  100 - check.score(),
                  new Action(
                      "JOURNEY:" + check.key(),
                      0,
                      check.fix(),
                      check.label() + ": " + check.evidence(),
                      stage.label(),
                      null,
                      ActionState.TODO)));
        }
      }
    }
    other.sort(Comparator.comparingDouble(ScoredAction::severity).reversed());
    other.forEach(s -> out.add(s.action()));
    return out;
  }

  private record ScoredAction(double severity, Action action) {}

  private static Headline headline(
      SizeSummary sizes,
      EstimatedMoney atRisk,
      List<RestockRow> restock,
      PromotedSoldOuts promoted,
      double soldOutPct,
      int soldOutVariants) {
    NumberFormat n = NumberFormat.getIntegerInstance(Locale.US);
    if (sizes.productsMissingCoreSizes() > 0) {
      StringBuilder text =
          new StringBuilder(
              n.format(sizes.productsMissingCoreSizes()) + " products are missing core sizes");
      if (sizes.onlyOneSizeLeft() > 0) {
        text.append("; ")
            .append(n.format(sizes.onlyOneSizeLeft()))
            .append(sizes.onlyOneSizeLeft() == 1 ? " style has" : " styles have")
            .append(" only one size left");
      }
      text.append('.');
      if (!restock.isEmpty() && new BigDecimal(atRisk.amount()).signum() > 0) {
        text.append(" About ").append(money(atRisk)).append(" a week is at risk (estimate).");
      }
      return new Headline(text.toString(), sizes.productsMissingCoreSizes(), "sizeGaps");
    }
    if (!promoted.products().isEmpty()) {
      return new Headline(
          n.format(promoted.products().size()) + " sold-out products are still promoted.",
          promoted.products().size(),
          "promotedSoldOuts");
    }
    return new Headline(
        soldOutPct
            + "% of all size and colour variants are sold out ("
            + n.format(soldOutVariants)
            + ").",
        soldOutVariants,
        "kpis");
  }

  private static final int MAX_CATEGORIES = 50;

  /** Product types by how many products use them (category filters). */
  private static List<String> categories(CatalogSnapshotData data) {
    Map<String, Integer> counts = new HashMap<>();
    data.products()
        .forEach(
            p -> {
              if (p.productType() != null && !p.productType().isBlank()) {
                counts.merge(p.productType().strip(), 1, Integer::sum);
              }
            });
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(MAX_CATEGORIES)
        .map(Map.Entry::getKey)
        .toList();
  }

  private static List<ExclusionCount> exclusions(StoreView store) {
    Map<ExclusionReason, Integer> counts = new EnumMap<>(ExclusionReason.class);
    store.products().forEach(v -> v.exclusion().ifPresent(r -> counts.merge(r, 1, Integer::sum)));
    List<ExclusionCount> out = new ArrayList<>();
    counts.forEach((reason, count) -> out.add(new ExclusionCount(reason, count, describe(reason))));
    return out;
  }

  private static List<ExcludedProduct> excludedProducts(StoreView store) {
    return store.products().stream()
        .filter(ProductView::excluded)
        .limit(MAX_EXCLUDED_LISTED)
        .map(
            v ->
                new ExcludedProduct(
                    v.product().externalId(),
                    v.product().title(),
                    v.productUrl(),
                    v.exclusion().get()))
        .toList();
  }

  static String describe(ExclusionReason reason) {
    return switch (reason) {
      case PRE_ORDER -> "Pre-order or coming soon (tagged): not stocked yet";
      case BACK_ORDER -> "Back-order (tagged): the store already expects more stock";
      case NON_PHYSICAL -> "Gift cards, gifts with purchase, memberships, donations: not stock";
      case BUNDLE -> "Bundles: stock follows the items inside";
      case LIKELY_DISCONTINUED ->
          "Likely discontinued: every size sold out, not a best seller, was discounted";
    };
  }

  private static List<String> dataNotes(
      CatalogSnapshotData data, ChangeContext changes, SizeSummary sizes) {
    List<String> notes = new ArrayList<>();
    notes.add(
        "Timing comes from snapshot windows only; the store's updated_at timestamps are not used (they reflect bulk syncs).");
    notes.add(
        "Variants set to \"continue selling when out of stock\" look in stock, so sold-out counts may be low.");
    notes.add(
        "$ at risk is an estimate: price × demand score × baseline units/week × share of sizes sold out (see Assumptions).");
    if (!changes.hasPrevious()) {
      notes.add("First snapshot: changes and sell-out speed are available after the next check.");
    }
    if (data.catalog().capped()) {
      notes.add(
          "Catalog capped at 25,000 products (public feed limit): store-wide totals are partial.");
    }
    if (data.catalog().sampled()) {
      notes.add("Sampled: " + data.productCount() + " products read from product pages.");
    }
    if (data.catalog().skippedMalformed() > 0) {
      notes.add(
          data.catalog().skippedMalformed()
              + " products were skipped because their data couldn't be read.");
    }
    if (sizes.unrecognised() > 0) {
      notes.add(
          sizes.unrecognised()
              + " products have size formats that weren't recognised; they are left out of size analysis, not guessed.");
    }
    if (data.robots().state() == RobotsInfo.State.MISSING) {
      notes.add("The store has no robots.txt; all public pages were allowed.");
    }
    if (!data.robots().blocked().isEmpty()) {
      notes.add(
          data.robots().blocked().size()
              + " pages were skipped because the store's robots.txt disallows them.");
    }
    return notes;
  }

  private static String summarise(List<String> sizes) {
    if (sizes.isEmpty()) {
      return "sold-out sizes";
    }
    if (sizes.size() <= 4) {
      return "sizes " + String.join(", ", sizes);
    }
    return sizes.size() + " sizes sold out";
  }

  private static String money(EstimatedMoney m) {
    BigDecimal amount = new BigDecimal(m.amount()).setScale(0, RoundingMode.HALF_UP);
    String formatted = NumberFormat.getIntegerInstance(Locale.US).format(amount);
    return m.currency() == null
        ? formatted + " (currency unknown)"
        : formatted + " " + m.currency();
  }
}

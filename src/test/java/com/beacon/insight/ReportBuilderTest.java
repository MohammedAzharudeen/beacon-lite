package com.beacon.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.action.ActionState;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.CollectionsData;
import com.beacon.config.Assumptions;
import com.beacon.config.AssumptionsLoader;
import com.beacon.insight.report.InsightReport;
import com.beacon.insight.report.RestockRow;
import com.beacon.journey.CheckStatus;
import com.beacon.journey.JourneyCheck;
import com.beacon.journey.JourneyStage;
import com.beacon.journey.JourneyStageKey;
import com.beacon.testsupport.DemoSnapshots;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression tests on the recorded real snapshots (3 Oct 2026). Expected values are what the
 * recordings contain; they drift when snapshots are re-recorded.
 */
class ReportBuilderTest {

  private static final Assumptions ASSUMPTIONS = AssumptionsLoader.packaged();
  private static final InsightReport STEVE_MADDEN = build(DemoSnapshots.steveMadden());
  private static final InsightReport REEBOK = build(DemoSnapshots.reebok());
  private static final InsightReport PETAL_AND_PUP = build(DemoSnapshots.first("petalandpup.com"));

  @Test
  void steveMadden_headlineAndCatalogFacts() {
    assertThat(STEVE_MADDEN.catalog().products()).isEqualTo(2512);
    assertThat(STEVE_MADDEN.sizeSummary().productsMissingCoreSizes()).isEqualTo(789);
    assertThat(STEVE_MADDEN.headline().text()).startsWith("789 products are missing core sizes");
    assertThat(STEVE_MADDEN.pricing().compareAtEqualsPrice()).isEqualTo(1342);
    assertThat(STEVE_MADDEN.catalogQuality().fewImages()).isEqualTo(14);
    assertThat(STEVE_MADDEN.kpis().sizesSoldOutPct()).isEqualTo(33.9);
  }

  @Test
  void steveMadden_preAndBackOrderExcluded() {
    assertThat(
            count(STEVE_MADDEN, ExclusionReason.PRE_ORDER)
                + count(STEVE_MADDEN, ExclusionReason.BACK_ORDER))
        .isEqualTo(283);
    assertThat(count(STEVE_MADDEN, ExclusionReason.BUNDLE)).isPositive();
    assertThat(count(STEVE_MADDEN, ExclusionReason.NON_PHYSICAL)).isPositive();
    assertThat(STEVE_MADDEN.restock())
        .noneMatch(
            r ->
                STEVE_MADDEN.excludedProducts().stream()
                    .anyMatch(e -> e.productId() == r.productId()));
  }

  @Test
  void steveMadden_lowHomeLinks_noticeInsteadOfZero() {
    assertThat(STEVE_MADDEN.promotedSoldOuts().notice()).contains("JavaScript");
  }

  @Test
  void reebok_membershipAndBundlesExcluded() {
    assertThat(REEBOK.excludedProducts())
        .anyMatch(
            e -> e.reason() == ExclusionReason.NON_PHYSICAL && e.title().contains("Membership"));
    assertThat(count(REEBOK, ExclusionReason.BUNDLE)).isPositive();
  }

  @Test
  void restock_rankedByAtRisk_everyRowHasASignalAndEstimate() {
    for (InsightReport report : new InsightReport[] {STEVE_MADDEN, REEBOK, PETAL_AND_PUP}) {
      assertThat(report.restock()).isNotEmpty();
      assertThat(report.restock())
          .allSatisfy(
              r -> {
                assertThat(r.signals()).isNotEmpty();
                assertThat(r.atRiskPerWeek().estimate()).isTrue();
                assertThat(r.soldOutSizes()).isPositive();
              });
      BigDecimal previous = null;
      for (RestockRow r : report.restock()) {
        BigDecimal value = new BigDecimal(r.atRiskPerWeek().amount());
        if (previous != null) {
          assertThat(value).isLessThanOrEqualTo(previous);
        }
        previous = value;
      }
    }
  }

  @Test
  void noBestSellerLabel_withoutBestSellerCollection() {
    CatalogSnapshotData data = DemoSnapshots.reebok();
    CatalogSnapshotData withoutCollections =
        data.withSignals(data.signals(), CollectionsData.empty(), data.robots());

    InsightReport report = build(withoutCollections);

    assertThat(report.restock())
        .noneMatch(r -> r.signals().contains(DemandSignal.BEST_SELLER_COLLECTION));
    assertThat(check(report, JourneyStageKey.BROWSE, "SOLD_OUT_BEST_SELLERS").status())
        .isEqualTo(CheckStatus.NOT_AVAILABLE);
  }

  @Test
  void journey_robotsBlockedChecksShownNotGuessed() {
    assertThat(check(REEBOK, JourneyStageKey.BROWSE, "SEARCH_TEST").status())
        .isEqualTo(CheckStatus.NOT_CHECKED_ROBOTS);
    assertThat(check(PETAL_AND_PUP, JourneyStageKey.BROWSE, "SEARCH_TEST").status())
        .isEqualTo(CheckStatus.NOT_CHECKED_ROBOTS);
    assertThat(check(STEVE_MADDEN, JourneyStageKey.BROWSE, "SEARCH_TEST").status())
        .isEqualTo(CheckStatus.CHECKED);
    JourneyStage browse = stage(REEBOK, JourneyStageKey.BROWSE);
    assertThat(browse.checksRun()).isEqualTo(2);
    assertThat(browse.checksTotal()).isEqualTo(3);
  }

  @Test
  void journey_returnsWindow_policyOrFooterPage() {
    JourneyCheck sm = check(STEVE_MADDEN, JourneyStageKey.CART_CHECKOUT, "RETURNS_WINDOW");
    JourneyCheck reebok = check(REEBOK, JourneyStageKey.CART_CHECKOUT, "RETURNS_WINDOW");
    JourneyCheck pp = check(PETAL_AND_PUP, JourneyStageKey.CART_CHECKOUT, "RETURNS_WINDOW");

    assertThat(sm.status()).isEqualTo(CheckStatus.CHECKED);
    assertThat(sm.evidence()).startsWith("30-day returns (/policies/refund-policy)");
    assertThat(reebok.status()).isEqualTo(CheckStatus.CHECKED_VIA_ALTERNATIVE);
    assertThat(reebok.evidence()).startsWith("30-day returns (/pages/returns-exchanges)");
    assertThat(pp.status()).isEqualTo(CheckStatus.NOT_AVAILABLE);
    assertThat(pp.reason()).contains("/pages/returns").contains("robots.txt");
  }

  @Test
  void catalogQuality_altTextIsSampledNeverStoreWide() {
    assertThat(STEVE_MADDEN.catalogQuality().altText().text())
        .isEqualTo("0 of 30 sampled products have alt text on all images");
  }

  @Test
  void topActions_mixRestockAndOther_dismissedDropOut() {
    assertThat(STEVE_MADDEN.topActions()).hasSize(5);
    assertThat(STEVE_MADDEN.topActions().subList(0, 3))
        .allMatch(a -> a.category().equals("Restock"));
    String firstKey = STEVE_MADDEN.topActions().get(0).actionKey();

    var reranked =
        ReportBuilder.topActions(STEVE_MADDEN.actions(), Map.of(firstKey, ActionState.DISMISSED));

    assertThat(reranked).hasSize(5).noneMatch(a -> a.actionKey().equals(firstKey));
    assertThat(reranked.get(0).rank()).isEqualTo(1);
  }

  @Test
  void report_reproducible_sameInputsSameOutput() {
    assertThat(build(DemoSnapshots.reebok())).isEqualTo(REEBOK);
    assertThat(REEBOK.assumptionsVersion()).hasSize(64);
  }

  private static InsightReport build(CatalogSnapshotData data) {
    return new ReportBuilder(ASSUMPTIONS)
        .build(1, 1, data, ChangeContext.firstSnapshot(), Map.of(), Instant.EPOCH);
  }

  private static int count(InsightReport report, ExclusionReason reason) {
    return report.exclusions().stream()
        .filter(e -> e.reason() == reason)
        .mapToInt(e -> e.count())
        .sum();
  }

  private static JourneyStage stage(InsightReport report, JourneyStageKey key) {
    return report.journey().stream().filter(s -> s.stage() == key).findFirst().orElseThrow();
  }

  private static JourneyCheck check(InsightReport report, JourneyStageKey key, String checkKey) {
    return stage(report, key).checks().stream()
        .filter(c -> c.key().equals(checkKey))
        .findFirst()
        .orElseThrow();
  }
}

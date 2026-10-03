package com.beacon.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.RobotsInfo;
import com.beacon.adapter.model.SourceStatus;
import com.beacon.adapter.model.StorefrontSignals;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.AssumptionsLoader;
import com.beacon.testsupport.RecordedStoreServer;
import com.beacon.testsupport.TestStores;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the Shopify adapter against real recorded store responses. */
class ShopifyAdapterTest {

  private static final Instant CAPTURED = Instant.parse("2026-10-03T07:31:21Z");

  private final ShopifyAdapter adapter =
      new ShopifyAdapter(
          new ObjectMapper(), AssumptionsLoader.packaged(), Clock.fixed(CAPTURED, ZoneOffset.UTC));

  @Test
  void supports_shopifyFeed_true() {
    try (RecordedStoreServer server = server("stevemadden")) {
      assertThat(adapter.supports(TestStores.session(server.baseUri()))).isTrue();
    }
  }

  @Test
  void probe_cloudflareChallenge_reportsBotProtection() {
    try (RecordedStoreServer server = server("meshki")) {
      assertThat(adapter.probe(TestStores.session(server.baseUri())))
          .isEqualTo(ShopifyAdapter.FeedProbe.BOT_PROTECTION);
    }
  }

  @Test
  void fetchCatalog_stopsAtFirstEmptyPage() {
    try (RecordedStoreServer server = server("stevemadden")) {
      List<Integer> progress = new ArrayList<>();
      CatalogSnapshotData data =
          adapter.fetchCatalog(
              TestStores.session(server.baseUri()), (read, est) -> progress.add(read));

      assertThat(data.productCount()).isEqualTo(250);
      assertThat(data.catalog().pagesRead()).isEqualTo(2);
      assertThat(data.catalog().capped()).isFalse();
      assertThat(data.capturedAt()).isEqualTo(CAPTURED);
      assertThat(progress).containsExactly(250);
      assertThat(server.requested())
          .contains("/products.json?limit=250&page=2")
          .doesNotContain("/products.json?limit=250&page=3");
    }
  }

  @Test
  void fetchCatalog_parsesProductsVariantsAndOptions() {
    try (RecordedStoreServer server = server("stevemadden")) {
      CatalogSnapshotData data =
          adapter.fetchCatalog(TestStores.session(server.baseUri()), ProgressListener.NONE);
      ProductData first = data.products().get(0);

      assertThat(first.externalId()).isPositive();
      assertThat(first.handle()).isNotBlank();
      assertThat(first.variants()).isNotEmpty();
      assertThat(first.variants().get(0).price()).isNotNull();
      assertThat(first.options()).anyMatch(o -> o.name().toLowerCase().contains("size"));
      assertThat(data.variantCount()).isGreaterThan(data.productCount());
    }
  }

  @Test
  void fetchSignals_steveMadden_readsPolicySearchAndPages() {
    try (RecordedStoreServer server = server("stevemadden")) {
      var session = TestStores.session(server.baseUri());
      CatalogSnapshotData data =
          adapter.fetchSignals(session, adapter.fetchCatalog(session, ProgressListener.NONE));
      StorefrontSignals s = data.signals();

      assertThat(data.store().currency()).isEqualTo("USD");
      assertThat(s.returns().status()).isEqualTo(SourceStatus.FETCHED);
      assertThat(s.returns().path()).isEqualTo("/policies/refund-policy");
      assertThat(s.returns().returnDays()).isEqualTo(30);
      assertThat(s.shipping().threshold()).isEqualByComparingTo("75");
      assertThat(s.search().status()).isEqualTo(SourceStatus.FETCHED);
      assertThat(s.home().productHandles()).hasSize(2);
      assertThat(s.home().apps()).contains("SWYM", "KLAVIYO", "AFTERPAY");
      assertThat(data.collections().bestSeller())
          .anySatisfy(
              c -> {
                assertThat(c.handle()).isEqualTo("best-sellers-all-products");
                assertThat(c.productExternalIds()).hasSize(347);
                assertThat(c.complete()).isTrue();
              });
      assertThat(s.productPages())
          .filteredOn(p -> p.status() == SourceStatus.FETCHED)
          .allSatisfy(p -> assertThat(p.notifyMeText()).isTrue());
      assertThat(s.altText().sampled()).isEqualTo(3);
      assertThat(data.robots().state()).isEqualTo(RobotsInfo.State.LOADED);
    }
  }

  @Test
  void fetchSignals_reebok_usesFooterPageAndNeverRequestsBlockedPaths() {
    try (RecordedStoreServer server = server("reebok")) {
      var session = TestStores.session(server.baseUri());
      CatalogSnapshotData data =
          adapter.fetchSignals(session, adapter.fetchCatalog(session, ProgressListener.NONE));
      StorefrontSignals s = data.signals();

      assertThat(s.returns().status()).isEqualTo(SourceStatus.FETCHED_ALTERNATIVE);
      assertThat(s.returns().path()).isEqualTo("/pages/returns-exchanges");
      assertThat(s.returns().robotsRule()).isEqualTo("Disallow: /policies/");
      assertThat(s.returns().returnDays()).isEqualTo(30);
      assertThat(s.search().status()).isEqualTo(SourceStatus.BLOCKED_BY_ROBOTS);
      assertThat(s.search().robotsRule()).isEqualTo("Disallow: /search");
      assertThat(data.robots().blocked())
          .extracting(RobotsInfo.BlockedPath::path)
          .contains(
              "/policies/refund-policy",
              "/search/suggest.json?q=shoes&resources%5Btype%5D=product");
      assertThat(server.requested())
          .noneMatch(p -> p.startsWith("/policies") || p.startsWith("/search"));
    }
  }

  @Test
  void fetchCatalog_botProtectionMidScan_failsWithClearCode() {
    try (RecordedStoreServer server = server("meshki")) {
      assertThatThrownBy(
              () ->
                  adapter.fetchCatalog(TestStores.session(server.baseUri()), ProgressListener.NONE))
          .isInstanceOf(BeaconException.class)
          .extracting(e -> ((BeaconException) e).code())
          .isEqualTo(ErrorCode.STORE_BLOCKS_AUTOMATION);
    }
  }

  @Test
  void searchTerms_mostCommonTypesLowerCased() {
    try (RecordedStoreServer server = server("stevemadden")) {
      CatalogSnapshotData data =
          adapter.fetchCatalog(TestStores.session(server.baseUri()), ProgressListener.NONE);
      List<String> terms = ShopifyAdapter.searchTerms(data.products(), 5);

      assertThat(terms).hasSizeLessThanOrEqualTo(5).allMatch(t -> t.equals(t.toLowerCase()));
    }
  }

  private static RecordedStoreServer server(String store) {
    return RecordedStoreServer.start(Path.of("src/test/resources/fixtures", store, "recording"));
  }
}

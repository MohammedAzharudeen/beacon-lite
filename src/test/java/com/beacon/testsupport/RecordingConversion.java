package com.beacon.testsupport;

import com.beacon.adapter.Platform;
import com.beacon.adapter.ProgressListener;
import com.beacon.adapter.ShopifyAdapter;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.StoreInfo;
import com.beacon.common.BeaconException;
import com.beacon.config.Assumptions;
import com.beacon.fetch.FetchSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the real Shopify adapter against a raw recording and labels the result with the real store.
 */
public final class RecordingConversion {

  private static final Logger log = LoggerFactory.getLogger(RecordingConversion.class);

  private RecordingConversion() {}

  public static CatalogSnapshotData convert(
      Path recording, String host, Instant capturedAt, Assumptions assumptions) {
    try (RecordedStoreServer server = RecordedStoreServer.start(recording)) {
      FetchSession session = TestStores.session(server.baseUri());
      ShopifyAdapter adapter =
          new ShopifyAdapter(
              new ObjectMapper(), assumptions, Clock.fixed(capturedAt, ZoneOffset.UTC));
      CatalogSnapshotData catalog = adapter.fetchCatalog(session, ProgressListener.NONE);
      CatalogSnapshotData full = adapter.fetchSignals(session, catalog);
      return full.withStore(new StoreInfo(host, Platform.SHOPIFY, full.store().currency()));
    }
  }

  /**
   * Like {@link #convert} but skips recordings the adapter can't read (e.g. bot-protection pages).
   */
  public static Optional<CatalogSnapshotData> tryConvert(
      Path recording, String host, Instant capturedAt, Assumptions assumptions) {
    try {
      return Optional.of(convert(recording, host, capturedAt, assumptions));
    } catch (BeaconException e) {
      log.warn("[CONVERT] store={} at={} skipped code={}", host, capturedAt, e.code());
      return Optional.empty();
    }
  }
}

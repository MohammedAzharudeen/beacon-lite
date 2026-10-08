package com.beacon.snapshot;

import com.beacon.adapter.AdapterRegistry;
import com.beacon.adapter.StoreAdapter;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.catalog.CatalogUpsertService;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.common.Metrics;
import com.beacon.config.Assumptions;
import com.beacon.config.BeaconProperties;
import com.beacon.diff.SnapshotWindow;
import com.beacon.fetch.FetchSession;
import com.beacon.fetch.PoliteHttpClient;
import com.beacon.insight.ReportService;
import com.beacon.insight.StoreView;
import com.beacon.job.JobProgress;
import com.beacon.job.JobStep;
import com.beacon.security.StoreUrl;
import com.beacon.security.UrlGuard;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Takes snapshots: fetch → raw file (atomic) → catalog + changes → report. A new snapshot becomes
 * current only on success; on failure the previous one stays current.
 */
@Service
public class SnapshotService {

  private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

  private final StoreRepository stores;
  private final SnapshotRepository snapshots;
  private final SnapshotStore snapshotStore;
  private final AdapterRegistry adapters;
  private final PoliteHttpClient http;
  private final UrlGuard urlGuard;
  private final CatalogUpsertService upsert;
  private final ReportService reports;
  private final JobProgress progress;
  private final TransactionTemplate tx;
  private final Assumptions assumptions;
  private final Metrics metrics;
  private final Clock clock;
  private final boolean demo;

  public SnapshotService(
      StoreRepository stores,
      SnapshotRepository snapshots,
      SnapshotStore snapshotStore,
      AdapterRegistry adapters,
      PoliteHttpClient http,
      UrlGuard urlGuard,
      CatalogUpsertService upsert,
      ReportService reports,
      JobProgress progress,
      TransactionTemplate tx,
      Assumptions assumptions,
      Metrics metrics,
      Clock clock,
      BeaconProperties properties) {
    this.stores = stores;
    this.snapshots = snapshots;
    this.snapshotStore = snapshotStore;
    this.adapters = adapters;
    this.http = http;
    this.urlGuard = urlGuard;
    this.upsert = upsert;
    this.reports = reports;
    this.progress = progress;
    this.tx = tx;
    this.assumptions = assumptions;
    this.metrics = metrics;
    this.clock = clock;
    this.demo = properties.demo();
  }

  /** Runs a full scan of a store for a job. Never throws: failures are recorded on the job. */
  public void capture(long storeId, UUID jobId) {
    MDC.put("storeId", Long.toString(storeId));
    MDC.put("jobId", jobId.toString());
    long started = System.nanoTime();
    Snapshot snapshot = null;
    try {
      Store store =
          stores.findById(storeId).orElseThrow(() -> new BeaconException(ErrorCode.NOT_FOUND));
      log.info("[CAPTURE] store={} job={} start", storeId, jobId);
      snapshot =
          snapshots.save(
              Snapshot.running(storeId, CatalogSnapshotData.SCHEMA_VERSION, clock.instant()));
      progress.step(jobId, JobStep.DETECT);
      // Demo mode replays recordings and never resolves or contacts the store
      StoreUrl url =
          demo
              ? new StoreUrl(store.getDomain(), URI.create("https://" + store.getDomain()))
              : urlGuard.checkUserInput(store.getDomain());
      http.refreshRobots(url.host());
      FetchSession session = new FetchSession(url, http);
      StoreAdapter adapter = adapters.forPlatform(store.getPlatform());
      progress.step(jobId, JobStep.FETCH_CATALOG);
      Integer estimate = previousProductCount(storeId).orElse(null);
      CatalogSnapshotData catalog =
          adapter.fetchCatalog(
              session, (read, est) -> progress.progress(jobId, read, est != null ? est : estimate));
      progress.step(jobId, JobStep.FETCH_SIGNALS);
      CatalogSnapshotData data = adapter.fetchSignals(session, catalog);
      progress.step(jobId, JobStep.BUILD_INSIGHTS);
      ingest(store.getId(), snapshot, data);
      progress.succeed(jobId);
      metrics.increment("jobs.completed");
      log.info(
          "[CAPTURE] store={} job={} done products={} ms={}",
          storeId,
          jobId,
          data.productCount(),
          (System.nanoTime() - started) / 1_000_000);
    } catch (BeaconException e) {
      fail(storeId, jobId, snapshot, e.code(), e.getMessage(), e.hint());
      log.warn(
          "[CAPTURE] store={} job={} failed code={} reason={}",
          storeId,
          jobId,
          e.code(),
          e.getMessage());
    } catch (RuntimeException e) {
      fail(
          storeId,
          jobId,
          snapshot,
          ErrorCode.INTERNAL_ERROR,
          "Unexpected error",
          ErrorCode.INTERNAL_ERROR.defaultHint());
      log.error("[CAPTURE] store={} job={} unexpected", storeId, jobId, e);
    } finally {
      metrics.record("job.duration_ms", (System.nanoTime() - started) / 1_000_000);
      MDC.remove("storeId");
      MDC.remove("jobId");
    }
  }

  /**
   * Stores recorded data as a new snapshot (demo seeding, CLI offline mode). The snapshot time is
   * the recording's capture time.
   */
  public long ingestRecorded(long storeId, CatalogSnapshotData data) {
    Snapshot snapshot =
        snapshots.save(Snapshot.running(storeId, data.schemaVersion(), data.capturedAt()));
    ingest(storeId, snapshot, data);
    return snapshot.getId();
  }

  private void ingest(long storeId, Snapshot snapshot, CatalogSnapshotData data) {
    String path = snapshotStore.writeAtomic(storeId, data);
    Optional<Snapshot> previous =
        snapshots.findTopByStoreIdAndStatusOrderByStartedAtDesc(storeId, SnapshotStatus.COMPLETE);
    SnapshotWindow window =
        previous
            .map(
                p ->
                    new SnapshotWindow(
                        p.getId(), snapshot.getId(), p.getStartedAt(), snapshot.getStartedAt()))
            .orElse(null);
    StoreView view = StoreView.build(data, assumptions, Set.of(), previous.isPresent());
    tx.executeWithoutResult(
        status -> {
          upsert.upsert(storeId, snapshot.getId(), view, window);
          Snapshot managed = snapshots.findById(snapshot.getId()).orElseThrow();
          managed.complete(path, data.productCount(), data.variantCount(), clock.instant());
          Store store = stores.findById(storeId).orElseThrow();
          if (store.getCurrentSnapshotId() == null && data.signals() != null) {
            store.rename(data.signals().home().siteName());
          }
          store.activate(managed.getId(), data.store().currency());
          // Same transaction: a stopped app never leaves a complete snapshot without its report
          reports.generate(storeId, snapshot.getId(), data, previous.isPresent());
        });
  }

  /**
   * Snapshots left running by a stopped app can't finish; mark them failed so they are never
   * mistaken for data. Runs before the demo loader, which resumes from the last complete one.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(-1)
  public void failInterruptedSnapshots() {
    Instant now = clock.instant();
    tx.executeWithoutResult(
        status ->
            snapshots
                .findByStatus(SnapshotStatus.RUNNING)
                .forEach(
                    s -> {
                      log.warn(
                          "[SNAPSHOT] store={} snapshot={} interrupted by restart",
                          s.getStoreId(),
                          s.getId());
                      s.fail(
                          ErrorCode.INTERNAL_ERROR.name(),
                          "The app stopped during this check; refresh to try again",
                          now);
                    }));
  }

  private Optional<Integer> previousProductCount(long storeId) {
    return snapshots
        .findTopByStoreIdAndStatusOrderByStartedAtDesc(storeId, SnapshotStatus.COMPLETE)
        .map(Snapshot::getProductCount);
  }

  private void fail(
      long storeId, UUID jobId, Snapshot snapshot, ErrorCode code, String message, String hint) {
    metrics.increment("jobs.failed");
    Instant now = clock.instant();
    tx.executeWithoutResult(
        status -> {
          if (snapshot != null && snapshot.getId() != null) {
            snapshots
                .findById(snapshot.getId())
                .ifPresent(s -> s.fail(code.name(), truncate(message), now));
          }
          stores.findById(storeId).ifPresent(Store::markFirstScanFailed);
        });
    progress.fail(jobId, code, hint);
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}

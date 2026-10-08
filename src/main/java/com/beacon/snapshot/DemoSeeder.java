package com.beacon.snapshot;

import com.beacon.adapter.Platform;
import com.beacon.adapter.ReplayAdapter;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.store.StoreService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads the recorded demo stores so there is something to look at straight away. Recordings are
 * real store data captured with robots.txt obeyed; each becomes a snapshot, oldest first, so the
 * demo has history (changes, trends, selling-fast). Live mode loads them only into an empty
 * database; demo mode also resumes a load that was interrupted. Progress is exposed for the UI.
 */
@Component
public class DemoSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

  private final StoreRepository stores;
  private final SnapshotRepository snapshotRows;
  private final ReplayAdapter recordings;
  private final SnapshotService snapshots;
  private final boolean enabled;
  private final boolean demo;
  private final List<String> order;

  private volatile boolean loading;
  private volatile int total;
  private final AtomicInteger loaded = new AtomicInteger();

  /**
   * @param loading true while recordings are being loaded; the dashboard shows a notice and
   *     refreshes when it turns false
   */
  public record Progress(boolean loading, int loaded, int total) {}

  public DemoSeeder(
      StoreRepository stores,
      SnapshotRepository snapshotRows,
      ReplayAdapter recordings,
      SnapshotService snapshots,
      @Value("${beacon.seed-demo-stores:true}") boolean enabled,
      @Value("${beacon.demo:false}") boolean demo,
      @Value("${beacon.demo-stores:}") List<String> order) {
    this.stores = stores;
    this.snapshotRows = snapshotRows;
    this.recordings = recordings;
    this.snapshots = snapshots;
    this.enabled = enabled;
    this.demo = demo;
    this.order = List.copyOf(order);
  }

  public Progress progress() {
    return new Progress(loading, loaded.get(), total);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(0)
  public void seed() {
    if (!enabled || (!demo && stores.count() > 0)) {
      return;
    }
    // Configured order first (the hero store becomes the default view), then any other recordings
    List<String> domains =
        new ArrayList<>(order.stream().filter(recordings.domains()::contains).toList());
    recordings.domains().stream().filter(d -> !domains.contains(d)).forEach(domains::add);
    total = domains.stream().mapToInt(recordings::count).sum();
    loaded.set(0);
    loading = true;
    try {
      for (String domain : domains) {
        seedStore(domain);
      }
    } finally {
      loading = false;
    }
  }

  /**
   * Adds the store if needed, then every recording newer than its latest complete snapshot (a
   * snapshot cut short by a stop was marked failed at startup, so its recording loads again).
   */
  private void seedStore(String domain) {
    Store store =
        stores
            .findByDomain(domain)
            .orElseGet(
                () ->
                    stores.save(
                        Store.adding(domain, StoreService.defaultName(domain), Platform.SHOPIFY)));
    Instant latest =
        snapshotRows
            .findTopByStoreIdAndStatusOrderByStartedAtDesc(store.getId(), SnapshotStatus.COMPLETE)
            .map(Snapshot::getStartedAt)
            .orElse(Instant.MIN);
    int count = recordings.count(domain);
    int added = 0;
    // Oldest first, one at a time: each recording becomes a snapshot with its own changes
    for (int i = 0; i < count; i++) {
      CatalogSnapshotData data = recordings.recording(domain, i);
      if (data.capturedAt().isAfter(latest)) {
        snapshots.ingestRecorded(store.getId(), data);
        added++;
      }
      loaded.incrementAndGet();
    }
    log.info(
        "[DEMO] store={} domain={} recordings={} added={}", store.getId(), domain, count, added);
  }
}

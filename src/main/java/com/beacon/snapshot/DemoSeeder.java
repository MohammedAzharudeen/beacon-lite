package com.beacon.snapshot;

import com.beacon.adapter.Platform;
import com.beacon.adapter.ReplayAdapter;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.store.StoreService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On first launch (empty database), loads the recorded demo stores so there is something to look at
 * straight away. Recordings are real store data captured with robots.txt obeyed.
 */
@Component
public class DemoSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

  private final StoreRepository stores;
  private final ReplayAdapter recordings;
  private final SnapshotService snapshots;
  private final boolean enabled;
  private final List<String> order;

  public DemoSeeder(
      StoreRepository stores,
      ReplayAdapter recordings,
      SnapshotService snapshots,
      @Value("${beacon.seed-demo-stores:true}") boolean enabled,
      @Value("${beacon.demo-stores:}") List<String> order) {
    this.stores = stores;
    this.recordings = recordings;
    this.snapshots = snapshots;
    this.enabled = enabled;
    this.order = List.copyOf(order);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(0)
  public void seed() {
    if (!enabled || stores.count() > 0) {
      return;
    }
    // Configured order first (the hero store becomes the default view), then any other recordings
    List<String> domains =
        new ArrayList<>(order.stream().filter(recordings.domains()::contains).toList());
    recordings.domains().stream().filter(d -> !domains.contains(d)).forEach(domains::add);
    for (String domain : domains) {
      List<CatalogSnapshotData> list = recordings.recordings(domain);
      Store store =
          stores.save(Store.adding(domain, StoreService.defaultName(domain), Platform.SHOPIFY));
      for (CatalogSnapshotData data : list) {
        snapshots.ingestRecorded(store.getId(), data);
      }
      log.info("[DEMO] store={} domain={} snapshots={}", store.getId(), domain, list.size());
    }
  }
}

package com.beacon.testsupport;

import com.beacon.adapter.Platform;
import com.beacon.snapshot.SnapshotService;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;

/** Loads the recorded Steve Madden and Reebok snapshots into the test database once. */
public final class DemoStores {

  private DemoStores() {}

  public static synchronized long steveMadden(StoreRepository stores, SnapshotService snapshots) {
    return load(stores, snapshots, "www.stevemadden.com");
  }

  public static synchronized long reebok(StoreRepository stores, SnapshotService snapshots) {
    return load(stores, snapshots, "www.reebok.com");
  }

  private static long load(StoreRepository stores, SnapshotService snapshots, String domain) {
    return stores
        .findByDomain(domain)
        .map(Store::getId)
        .orElseGet(
            () -> {
              Store store = stores.save(Store.adding(domain, domain, Platform.SHOPIFY));
              snapshots.ingestRecorded(store.getId(), DemoSnapshots.first(domain));
              return store.getId();
            });
  }
}

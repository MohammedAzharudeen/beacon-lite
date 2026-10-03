package com.beacon.snapshot;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** The current snapshot's full catalog per store, read from its raw file once and cached. */
@Component
public class CurrentCatalog {

  private final StoreRepository stores;
  private final SnapshotRepository snapshots;
  private final SnapshotStore files;
  private final Map<Long, Cached> byStore = new ConcurrentHashMap<>();

  public CurrentCatalog(StoreRepository stores, SnapshotRepository snapshots, SnapshotStore files) {
    this.stores = stores;
    this.snapshots = snapshots;
    this.files = files;
  }

  public CatalogSnapshotData of(long storeId) {
    Store store =
        stores.findById(storeId).orElseThrow(() -> new BeaconException(ErrorCode.NOT_FOUND));
    Long snapshotId = store.getCurrentSnapshotId();
    if (snapshotId == null) {
      throw new BeaconException(ErrorCode.REPORT_NOT_READY);
    }
    Cached cached = byStore.get(storeId);
    if (cached != null && cached.snapshotId() == snapshotId) {
      return cached.data();
    }
    Snapshot snapshot =
        snapshots
            .findById(snapshotId)
            .orElseThrow(() -> new BeaconException(ErrorCode.REPORT_NOT_READY));
    CatalogSnapshotData data = files.read(snapshot.getRawPath());
    byStore.put(storeId, new Cached(snapshotId, data));
    return data;
  }

  private record Cached(long snapshotId, CatalogSnapshotData data) {}
}

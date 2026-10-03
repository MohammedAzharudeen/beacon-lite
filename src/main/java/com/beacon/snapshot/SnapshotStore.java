package com.beacon.snapshot;

import com.beacon.adapter.model.CatalogSnapshotData;

/** Where raw snapshots are kept (interface at the storage boundary). */
public interface SnapshotStore {

  /**
   * Writes the snapshot so that a crash never leaves a partial file.
   *
   * @return the stored file's path, recorded on the snapshot row
   */
  String writeAtomic(long storeId, CatalogSnapshotData data);

  CatalogSnapshotData read(String path);

  /** Deletes a stored snapshot file (retention). */
  void delete(String path);
}

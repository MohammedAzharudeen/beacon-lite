package com.beacon.snapshot;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

  Optional<Snapshot> findTopByStoreIdAndStatusOrderByStartedAtDesc(
      Long storeId, SnapshotStatus status);

  /** The most recent snapshot of any status (shows "Last refresh failed"). */
  Optional<Snapshot> findTopByStoreIdOrderByStartedAtDesc(Long storeId);

  /** Snapshots in one state, e.g. those left running by a stopped app. */
  List<Snapshot> findByStatus(SnapshotStatus status);

  /** Oldest first; used for retention and KPI trends. */
  List<Snapshot> findByStoreIdAndStatusOrderByStartedAtAsc(Long storeId, SnapshotStatus status);
}

package com.beacon.snapshot;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

  Optional<Snapshot> findTopByStoreIdAndStatusOrderByStartedAtDesc(
      Long storeId, SnapshotStatus status);

  /** Oldest first; used for retention and KPI trends. */
  List<Snapshot> findByStoreIdAndStatusOrderByStartedAtAsc(Long storeId, SnapshotStatus status);
}

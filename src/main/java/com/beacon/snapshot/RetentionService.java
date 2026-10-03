package com.beacon.snapshot;

import com.beacon.config.BeaconProperties;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raw snapshot retention: keep every file for {@code keepAllDays}, then one per UTC day (the oldest
 * of each day). Rows stay so reports and trends keep their references; the current snapshot's file
 * is never removed.
 */
@Service
public class RetentionService {

  private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

  private final SnapshotRepository snapshots;
  private final StoreRepository stores;
  private final SnapshotStore files;
  private final int keepAllDays;
  private final Clock clock;

  public RetentionService(
      SnapshotRepository snapshots,
      StoreRepository stores,
      SnapshotStore files,
      BeaconProperties properties,
      Clock clock) {
    this.snapshots = snapshots;
    this.stores = stores;
    this.files = files;
    this.keepAllDays = properties.snapshot().keepAllDays();
    this.clock = clock;
  }

  @Transactional
  public int applyAll() {
    int pruned = 0;
    for (Store store : stores.findAll()) {
      List<Snapshot> complete =
          snapshots.findByStoreIdAndStatusOrderByStartedAtAsc(
              store.getId(), SnapshotStatus.COMPLETE);
      for (Snapshot s :
          toPrune(complete, clock.instant(), keepAllDays, store.getCurrentSnapshotId())) {
        files.delete(s.getRawPath());
        s.clearRawFile();
        pruned++;
      }
    }
    if (pruned > 0) {
      log.info("[RETENTION] pruned={} raw snapshot files", pruned);
    }
    return pruned;
  }

  /** Snapshots whose raw file should be removed; input must be ordered oldest first. */
  static List<Snapshot> toPrune(
      List<Snapshot> oldestFirst, Instant now, int keepAllDays, Long currentId) {
    Instant cutoff = now.minus(Duration.ofDays(keepAllDays));
    Set<LocalDate> keptDays = new HashSet<>();
    List<Snapshot> prune = new ArrayList<>();
    for (Snapshot s : oldestFirst) {
      if (s.getRawPath() == null
          || !s.getStartedAt().isBefore(cutoff)
          || s.getId().equals(currentId)) {
        continue;
      }
      LocalDate day = s.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate();
      if (!keptDays.add(day)) {
        prune.add(s);
      }
    }
    return prune;
  }
}

package com.beacon.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.Platform;
import com.beacon.adapter.ReplayAdapter;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.insight.ReportService;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.testsupport.DemoSnapshots;
import com.beacon.testsupport.SnapshotEdits;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * A demo load stopped part-way: the cut-short snapshot is failed, and its recording loads again.
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoSeederResumeTest {

  private static final String DOMAIN = "resume-test.reebok.com";

  @Autowired SnapshotService snapshots;
  @Autowired SnapshotRepository snapshotRows;
  @Autowired StoreRepository stores;
  @Autowired ReportService reports;

  @Test
  void stoppedMidRecording_snapshotMarkedFailed_andResumeLoadsItAgain() {
    CatalogSnapshotData first = DemoSnapshots.reebok();
    CatalogSnapshotData second = SnapshotEdits.later(first, Duration.ofHours(6), list -> list);
    Store store = stores.save(Store.adding(DOMAIN, "Reebok", Platform.SHOPIFY));
    snapshots.ingestRecorded(store.getId(), first);
    // The app stopped while the second recording was being loaded
    Snapshot cut =
        snapshotRows.save(
            Snapshot.running(store.getId(), second.schemaVersion(), second.capturedAt()));

    snapshots.failInterruptedSnapshots();

    assertThat(snapshotRows.findById(cut.getId()).orElseThrow().getStatus())
        .isEqualTo(SnapshotStatus.FAILED);

    DemoSeeder seeder =
        new DemoSeeder(
            stores,
            snapshotRows,
            new ReplayAdapter(Map.of(DOMAIN, List.of(first, second))),
            snapshots,
            true,
            true,
            List.of(DOMAIN));
    seeder.seed();

    List<Snapshot> complete =
        snapshotRows.findByStoreIdAndStatusOrderByStartedAtAsc(
            store.getId(), SnapshotStatus.COMPLETE);
    assertThat(complete)
        .extracting(Snapshot::getStartedAt)
        .containsExactly(first.capturedAt(), second.capturedAt());
    assertThat(reports.require(store.getId()).snapshotId()).isEqualTo(complete.get(1).getId());
    assertThat(seeder.progress()).isEqualTo(new DemoSeeder.Progress(false, 2, 2));

    // Loading again adds nothing: every recording is already a complete snapshot
    seeder.seed();
    assertThat(
            snapshotRows.findByStoreIdAndStatusOrderByStartedAtAsc(
                store.getId(), SnapshotStatus.COMPLETE))
        .hasSize(2);
  }
}

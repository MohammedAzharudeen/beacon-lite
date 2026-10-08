package com.beacon.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.model.CatalogSnapshotData;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The bundled demo recordings: indexed by store folder, ordered by capture time, decoded on demand.
 */
class ReplayAdapterTest {

  private final ReplayAdapter replay = new ReplayAdapter();

  @Test
  void indexesEveryDemoStore_oldestFirst() {
    assertThat(replay.domains())
        .containsExactlyInAnyOrder("www.stevemadden.com", "www.reebok.com", "petalandpup.com");
    for (String domain : replay.domains()) {
      int n = replay.count(domain);
      assertThat(n).isGreaterThan(1);
      Instant previous = Instant.MIN;
      for (int i = 0; i < n; i++) {
        CatalogSnapshotData data = replay.recording(domain, i);
        assertThat(data.store().domain()).isEqualToIgnoringCase(domain);
        assertThat(data.capturedAt()).isAfter(previous);
        previous = data.capturedAt();
      }
    }
  }

  @Test
  void oldestRecording_isTheThirdOfOctoberBaseline() {
    assertThat(replay.recording("www.stevemadden.com", 0).capturedAt())
        .isEqualTo(Instant.parse("2026-10-03T07:31:21Z"));
    assertThat(replay.recording("www.stevemadden.com", 0).products()).hasSize(2512);
  }
}

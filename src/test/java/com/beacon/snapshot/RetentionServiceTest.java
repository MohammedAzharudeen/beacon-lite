package com.beacon.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

  @Test
  void toPrune_keepsRecentAllAndOldestPerOlderDay() {
    Snapshot recentA = snapshot(1, "2026-10-19T06:00:00Z");
    Snapshot recentB = snapshot(2, "2026-10-19T12:00:00Z");
    Snapshot oldDay1First = snapshot(3, "2026-10-01T00:00:00Z");
    Snapshot oldDay1Second = snapshot(4, "2026-10-01T06:00:00Z");
    Snapshot oldDay1Third = snapshot(5, "2026-10-01T18:00:00Z");
    Snapshot oldDay2Only = snapshot(6, "2026-10-02T06:00:00Z");

    List<Snapshot> prune =
        RetentionService.toPrune(
            List.of(oldDay1First, oldDay1Second, oldDay1Third, oldDay2Only, recentA, recentB),
            NOW,
            7,
            2L);

    assertThat(prune).containsExactly(oldDay1Second, oldDay1Third);
  }

  @Test
  void toPrune_neverTouchesCurrentSnapshot() {
    Snapshot first = snapshot(1, "2026-10-01T00:00:00Z");
    Snapshot current = snapshot(2, "2026-10-01T06:00:00Z");

    assertThat(RetentionService.toPrune(List.of(first, current), NOW, 7, 2L)).isEmpty();
  }

  @Test
  void toPrune_alreadyPrunedFilesIgnored() {
    Snapshot first = snapshot(1, "2026-10-01T00:00:00Z");
    Snapshot pruned = snapshot(2, "2026-10-01T06:00:00Z");
    pruned.clearRawFile();

    assertThat(RetentionService.toPrune(List.of(first, pruned), NOW, 7, 9L)).isEmpty();
  }

  private static Snapshot snapshot(long id, String startedAt) {
    Snapshot s = Snapshot.running(1L, 1, Instant.parse(startedAt));
    s.complete("data/snapshots/1/" + id + ".json.gz", 1, 1, Instant.parse(startedAt));
    try {
      Field f = Snapshot.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(s, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return s;
  }
}

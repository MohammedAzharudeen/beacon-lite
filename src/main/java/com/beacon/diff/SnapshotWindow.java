package com.beacon.diff;

import java.time.Instant;

/** The two snapshots a change was observed between, and their capture times. */
public record SnapshotWindow(Long fromSnapshotId, Long toSnapshotId, Instant start, Instant end) {}

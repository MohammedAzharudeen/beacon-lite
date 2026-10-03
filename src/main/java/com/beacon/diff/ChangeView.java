package com.beacon.diff;

import java.time.Instant;

/**
 * A change event ready for display: the product and size involved, and the time window (never an
 * exact time, since timing comes only from snapshots).
 */
public record ChangeView(
    long id,
    ChangeType type,
    Long productId,
    String productTitle,
    String productUrl,
    String size,
    String oldValue,
    String newValue,
    Instant windowStart,
    Instant windowEnd) {}

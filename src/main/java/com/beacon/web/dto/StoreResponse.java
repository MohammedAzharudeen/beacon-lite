package com.beacon.web.dto;

import java.time.Instant;

/**
 * A tracked store.
 *
 * @param lastFailure set when the most recent scan failed; the previous results stay visible
 */
public record StoreResponse(
    Long id,
    String domain,
    String displayName,
    String platform,
    String currency,
    String status,
    Long currentSnapshotId,
    Instant lastCheckedAt,
    Instant nextCheckAt,
    LastFailure lastFailure) {

  /** "Last refresh failed: …" */
  public record LastFailure(String code, String message, Instant at) {}
}

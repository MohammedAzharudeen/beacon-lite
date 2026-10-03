package com.beacon.fetch;

/** What happened to a request. */
public enum FetchOutcome {
  /** A response was received (any HTTP status). */
  RESPONSE,
  /** Not sent: the store's robots.txt disallows the path. */
  BLOCKED_BY_ROBOTS,
  /** Aborted: the response was larger than the configured cap. */
  TOO_LARGE
}

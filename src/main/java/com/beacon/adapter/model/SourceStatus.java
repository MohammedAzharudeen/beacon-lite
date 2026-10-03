package com.beacon.adapter.model;

/** How a piece of storefront data was obtained, so reports can say why something wasn't checked. */
public enum SourceStatus {
  /** Read from the usual page. */
  FETCHED,
  /** The usual page was blocked or missing; an alternative page was read instead. */
  FETCHED_ALTERNATIVE,
  /** Not fetched: the store's robots.txt disallows it. */
  BLOCKED_BY_ROBOTS,
  /** The page doesn't exist or held nothing usable. */
  NOT_FOUND,
  /** The request failed (network, size cap, unreadable content). */
  FAILED
}

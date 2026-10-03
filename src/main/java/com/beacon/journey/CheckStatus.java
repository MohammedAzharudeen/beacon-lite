package com.beacon.journey;

/** Outcome of one journey check. */
public enum CheckStatus {
  CHECKED,
  CHECKED_VIA_ALTERNATIVE,
  NOT_CHECKED_ROBOTS,
  NOT_VERIFIABLE,
  NOT_AVAILABLE
}

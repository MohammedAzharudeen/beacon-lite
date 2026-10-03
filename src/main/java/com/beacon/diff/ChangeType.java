package com.beacon.diff;

/** Kind of change detected between two snapshots. */
public enum ChangeType {
  SOLD_OUT,
  RESTOCKED,
  PRICE_CHANGED,
  PRODUCT_ADDED,
  PRODUCT_REMOVED,
  VARIANT_ADDED,
  VARIANT_REMOVED
}

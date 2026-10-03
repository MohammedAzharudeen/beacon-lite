package com.beacon.insight;

/** Why a product is kept out of restock logic. */
public enum ExclusionReason {
  PRE_ORDER,
  BACK_ORDER,
  NON_PHYSICAL,
  BUNDLE,
  LIKELY_DISCONTINUED
}

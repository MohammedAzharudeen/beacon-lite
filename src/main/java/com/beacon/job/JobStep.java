package com.beacon.job;

/** Step a running job is on, shown as progress in the UI. */
public enum JobStep {
  DETECT,
  FETCH_CATALOG,
  FETCH_SIGNALS,
  BUILD_INSIGHTS
}

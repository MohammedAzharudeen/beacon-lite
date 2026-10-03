package com.beacon.job;

/** Progress state of a background job. */
public enum JobStatus {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED
}

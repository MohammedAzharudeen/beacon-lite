package com.beacon.adapter;

/** Receives catalog-reading progress for the job progress bar. */
@FunctionalInterface
public interface ProgressListener {

  ProgressListener NONE = (read, estimate) -> {};

  /**
   * @param productsRead products read so far
   * @param estimate expected total, or {@code null} when unknown
   */
  void onProgress(int productsRead, Integer estimate);
}

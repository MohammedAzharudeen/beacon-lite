package com.beacon.job;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** One scan per store at a time; a second request joins the running job. */
@Component
public class StoreLockRegistry {

  private final Map<Long, UUID> running = new ConcurrentHashMap<>();

  /** Claims the store for a job; returns the job already holding it, if any. */
  public Optional<UUID> claim(long storeId, UUID jobId) {
    return Optional.ofNullable(running.putIfAbsent(storeId, jobId));
  }

  public void release(long storeId, UUID jobId) {
    running.remove(storeId, jobId);
  }
}

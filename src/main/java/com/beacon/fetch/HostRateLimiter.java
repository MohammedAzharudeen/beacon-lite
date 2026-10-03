package com.beacon.fetch;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Allows one request at a time per store host, with a pause between requests, so scans stay polite
 * to the stores.
 */
class HostRateLimiter {

  private final Duration delay;
  private final Map<String, HostSlot> slots = new ConcurrentHashMap<>();

  HostRateLimiter(Duration delay) {
    this.delay = delay;
  }

  /** Blocks until this host may be called; the caller must call {@link #release(String)}. */
  void acquire(String host) throws InterruptedException {
    HostSlot slot = slots.computeIfAbsent(host, h -> new HostSlot());
    slot.lock.lockInterruptibly();
    long waitNanos = slot.lastFinishedNanos + delay.toNanos() - System.nanoTime();
    if (slot.lastFinishedNanos != 0 && waitNanos > 0) {
      Thread.sleep(Duration.ofNanos(waitNanos));
    }
  }

  void release(String host) {
    HostSlot slot = slots.get(host);
    slot.lastFinishedNanos = System.nanoTime();
    slot.lock.unlock();
  }

  private static final class HostSlot {
    private final ReentrantLock lock = new ReentrantLock(true);
    private long lastFinishedNanos;
  }
}

package com.beacon.common;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * In-memory counters and timers behind {@code GET /api/metrics}. Actuator and Micrometer are the
 * production upgrade.
 */
@Component
public class Metrics {

  private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
  private final Map<String, Timer> timers = new ConcurrentHashMap<>();

  public void increment(String name) {
    counters.computeIfAbsent(name, n -> new LongAdder()).increment();
  }

  public void record(String name, long millis) {
    timers.computeIfAbsent(name, n -> new Timer()).record(millis);
  }

  /** A sorted copy for the metrics endpoint. */
  public Map<String, Object> snapshot() {
    Map<String, Object> out = new TreeMap<>();
    counters.forEach((name, value) -> out.put(name, value.sum()));
    timers.forEach((name, timer) -> out.put(name, timer.summary()));
    return out;
  }

  private static final class Timer {
    private long count;
    private long totalMillis;
    private long maxMillis;

    synchronized void record(long millis) {
      count++;
      totalMillis += millis;
      maxMillis = Math.max(maxMillis, millis);
    }

    synchronized Map<String, Long> summary() {
      return Map.of(
          "count", count,
          "totalMs", totalMillis,
          "maxMs", maxMillis,
          "avgMs", count == 0 ? 0 : totalMillis / count);
    }
  }
}

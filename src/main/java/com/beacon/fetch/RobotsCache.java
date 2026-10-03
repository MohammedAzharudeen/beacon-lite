package com.beacon.fetch;

import com.beacon.adapter.model.RobotsInfo;
import com.beacon.robots.RobotsRules;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds each store's robots.txt rules. Rules are loaded once per host and refreshed by {@link
 * #invalidate(String)} at the start of every scan, so a store's latest file always applies.
 */
class RobotsCache {

  private static final Logger log = LoggerFactory.getLogger(RobotsCache.class);

  private final Map<String, Entry> entriesByHost = new ConcurrentHashMap<>();

  Entry get(String host) {
    return entriesByHost.get(host);
  }

  void put(String host, Entry entry) {
    entriesByHost.put(host, entry);
  }

  void invalidate(String host) {
    if (entriesByHost.remove(host) != null) {
      log.info("[ROBOTS] host={} cache cleared", host);
    }
  }

  record Entry(RobotsRules rules, RobotsInfo.State state) {}
}

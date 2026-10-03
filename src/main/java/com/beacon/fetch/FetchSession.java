package com.beacon.fetch;

import com.beacon.adapter.model.RobotsInfo;
import com.beacon.security.StoreUrl;
import java.util.ArrayList;
import java.util.List;

/**
 * One scan's view of a store: every request goes through the polite client, and every path skipped
 * because of robots.txt is remembered so the report can show it as "Not checked".
 */
public final class FetchSession {

  private final StoreUrl store;
  private final PoliteHttpClient http;
  private final List<RobotsInfo.BlockedPath> blocked = new ArrayList<>();

  public FetchSession(StoreUrl store, PoliteHttpClient http) {
    this.store = store;
    this.http = http;
  }

  public StoreUrl store() {
    return store;
  }

  /** Fetches a path on the store; robots-blocked paths are recorded and never requested. */
  public FetchResult get(String pathAndQuery) {
    FetchResult result = http.get(store, pathAndQuery);
    if (result.outcome() == FetchOutcome.BLOCKED_BY_ROBOTS) {
      synchronized (blocked) {
        blocked.add(new RobotsInfo.BlockedPath(pathAndQuery, result.robotsRule()));
      }
    }
    return result;
  }

  public RobotsInfo.State robotsState() {
    return http.robotsState(store);
  }

  public List<String> sitemaps() {
    return http.sitemaps(store);
  }

  public RobotsInfo robotsInfo() {
    synchronized (blocked) {
      return new RobotsInfo(robotsState(), blocked);
    }
  }
}

package com.beacon.adapter.model;

import java.util.List;

/**
 * What robots.txt allowed during the scan.
 *
 * @param state whether the file was read
 * @param blocked every path the scan skipped because robots.txt disallows it
 */
public record RobotsInfo(State state, List<BlockedPath> blocked) {

  public RobotsInfo {
    blocked = List.copyOf(blocked);
  }

  public enum State {
    /** robots.txt was read and obeyed. */
    LOADED,
    /** No robots.txt (4xx): everything allowed. */
    MISSING,
    /** robots.txt couldn't be read (5xx or timeout): nothing fetched. */
    UNAVAILABLE
  }

  /** A skipped path and the rule that blocked it. */
  public record BlockedPath(String path, String rule) {}
}

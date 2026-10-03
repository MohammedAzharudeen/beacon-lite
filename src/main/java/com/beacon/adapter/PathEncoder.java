package com.beacon.adapter;

import java.nio.charset.StandardCharsets;

/**
 * Percent-encodes a path or query value the same way as Python's {@code urllib.parse.quote}, so
 * live scans request exactly the URLs that were recorded for tests.
 */
final class PathEncoder {

  private static final String UNRESERVED =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-~";

  private PathEncoder() {}

  /** Encodes everything except unreserved characters and {@code /}. */
  static String quote(String value) {
    StringBuilder out = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if (b >= 0 && (UNRESERVED.indexOf(c) >= 0 || c == '/')) {
        out.append(c);
      } else {
        out.append('%').append(String.format("%02X", b & 0xFF));
      }
    }
    return out.toString();
  }
}

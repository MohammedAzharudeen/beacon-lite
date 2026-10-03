package com.beacon.testsupport;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.snapshot.SnapshotCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** The recorded demo snapshots (real store data), loaded once per test run. */
public final class DemoSnapshots {

  private static final Path ROOT = Path.of("src/main/resources/snapshots");
  private static final Map<String, CatalogSnapshotData> CACHE = new ConcurrentHashMap<>();

  private DemoSnapshots() {}

  public static CatalogSnapshotData steveMadden() {
    return first("www.stevemadden.com");
  }

  public static CatalogSnapshotData reebok() {
    return first("www.reebok.com");
  }

  /** The oldest recording of a store. */
  public static CatalogSnapshotData first(String domain) {
    return CACHE.computeIfAbsent(domain, DemoSnapshots::load);
  }

  private static CatalogSnapshotData load(String domain) {
    try (Stream<Path> files = Files.list(ROOT.resolve(domain))) {
      Path file =
          files.filter(p -> p.toString().endsWith(".json.gz")).sorted().findFirst().orElseThrow();
      try (InputStream in = Files.newInputStream(file)) {
        return SnapshotCodec.read(in);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

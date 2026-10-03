package com.beacon.insight;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.config.AssumptionsLoader;
import com.beacon.testsupport.RecordingConversion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the accuracy back-test over every recording collected by {@code scripts/record-stores.py}
 * (one timestamped folder per run). Only on request:
 *
 * <pre>./mvnw test -Dtest=BackTestRunner -Dbeacon.backtest=.bootstrap/recordings</pre>
 */
class BackTestRunner {

  private static final Logger log = LoggerFactory.getLogger(BackTestRunner.class);
  private static final DateTimeFormatter FOLDER_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

  @Test
  @EnabledIfSystemProperty(named = "beacon.backtest", matches = ".+")
  void backTestAllStores() throws IOException {
    Path root = Path.of(System.getProperty("beacon.backtest"));
    Map<String, List<CatalogSnapshotData>> byStore = new TreeMap<>();
    List<Path> runs;
    try (Stream<Path> dirs = Files.list(root)) {
      runs =
          dirs.filter(Files::isDirectory)
              .filter(d -> d.getFileName().toString().matches("\\d{4}-.*Z"))
              .sorted()
              .toList();
    }
    for (Path run : runs) {
      Instant at = Instant.from(FOLDER_TIME.parse(run.getFileName().toString()));
      try (Stream<Path> stores = Files.list(run)) {
        for (Path store : stores.filter(s -> Files.exists(s.resolve("manifest.json"))).toList()) {
          RecordingConversion.tryConvert(
                  store, store.getFileName().toString(), at, AssumptionsLoader.packaged())
              .ifPresent(
                  data ->
                      byStore
                          .computeIfAbsent(data.store().domain(), k -> new ArrayList<>())
                          .add(data));
        }
      }
    }
    BackTest backTest = new BackTest(AssumptionsLoader.packaged());
    byStore.forEach(
        (domain, snapshots) -> {
          BackTest.Result r = backTest.run(snapshots);
          log.info(
              "[BACKTEST] store={} snapshots={} flags={} evaluated={} hits={} hitRate={}",
              domain,
              r.snapshots(),
              r.flags(),
              r.evaluated(),
              r.hits(),
              r.hitRate() == null ? "n/a" : String.format("%.1f%%", 100 * r.hitRate()));
        });
  }
}

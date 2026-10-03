package com.beacon.adapter;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.config.Assumptions;
import com.beacon.config.AssumptionsLoader;
import com.beacon.snapshot.SnapshotCodec;
import com.beacon.testsupport.RecordingConversion;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns raw store recordings ({@code scripts/record-stores.py}) into replayable snapshots by
 * running the real {@link ShopifyAdapter} against them. Builds the demo snapshots in {@code
 * src/main/resources/snapshots/}. Runs only on request:
 *
 * <pre>
 * ./mvnw test -Dtest=RecordingConverter -Dbeacon.recordings=.bootstrap/recordings/&lt;time&gt; \
 *     -Dbeacon.snapshots-out=src/main/resources/snapshots
 * </pre>
 *
 * The capture time comes from the recording folder name (e.g. {@code 2026-10-03T07-31-21Z}).
 */
class RecordingConverter {

  private static final Logger log = LoggerFactory.getLogger(RecordingConverter.class);
  private static final DateTimeFormatter FOLDER_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

  @Test
  @EnabledIfSystemProperty(named = "beacon.recordings", matches = ".+")
  void convertRecordings() throws IOException {
    Path recordings = Path.of(System.getProperty("beacon.recordings"));
    Path outDir =
        Path.of(System.getProperty("beacon.snapshots-out", "src/main/resources/snapshots"));
    Instant capturedAt = Instant.from(FOLDER_TIME.parse(recordings.getFileName().toString()));
    try (Stream<Path> stores = Files.list(recordings)) {
      for (Path recording : stores.filter(p -> Files.exists(p.resolve("manifest.json"))).toList()) {
        String host = recording.getFileName().toString();
        CatalogSnapshotData data;
        try {
          data = convert(recording, host, capturedAt, AssumptionsLoader.packaged());
        } catch (BeaconException e) {
          log.warn("[CONVERT] store={} skipped code={} reason={}", host, e.code(), e.getMessage());
          continue;
        }
        Path target = outDir.resolve(host).resolve(FOLDER_TIME.format(capturedAt) + ".json.gz");
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target)) {
          SnapshotCodec.write(data, out);
        }
        log.info(
            "[CONVERT] store={} products={} variants={} file={} bytes={}",
            host,
            data.productCount(),
            data.variantCount(),
            target,
            Files.size(target));
      }
    }
  }

  static CatalogSnapshotData convert(
      Path recording, String host, Instant capturedAt, Assumptions assumptions) {
    return RecordingConversion.convert(recording, host, capturedAt, assumptions);
  }
}

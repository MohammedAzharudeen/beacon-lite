package com.beacon.snapshot;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.BeaconProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Raw snapshots as {@code <dir>/<storeId>/<timestamp>.json.gz}. Written to a temp file first and
 * renamed into place, so a crash mid-write never leaves a corrupt snapshot.
 */
@Component
public class GzipFileSnapshotStore implements SnapshotStore {

  private static final Logger log = LoggerFactory.getLogger(GzipFileSnapshotStore.class);
  private static final DateTimeFormatter FILE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

  private final Path root;

  @Autowired
  public GzipFileSnapshotStore(BeaconProperties properties) {
    this(Path.of(properties.snapshot().dir()));
  }

  public GzipFileSnapshotStore(Path root) {
    this.root = root;
  }

  @Override
  public String writeAtomic(long storeId, CatalogSnapshotData data) {
    Path dir = root.resolve(Long.toString(storeId));
    Path target = dir.resolve(FILE_TIME.format(data.capturedAt()) + ".json.gz");
    Path temp = null;
    try {
      Files.createDirectories(dir);
      temp = Files.createTempFile(dir, ".writing-", ".tmp");
      try (OutputStream out = Files.newOutputStream(temp)) {
        writeData(data, out);
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
      log.info("[SNAPSHOT] store={} file={} bytes={}", storeId, target, Files.size(target));
      return target.toString();
    } catch (IOException e) {
      deleteQuietly(temp);
      throw new BeaconException(
          ErrorCode.SNAPSHOT_WRITE_FAILED,
          "The scan couldn't be saved",
          "Check free disk space",
          e);
    }
  }

  /** Separate so tests can simulate a crash in the middle of writing. */
  protected void writeData(CatalogSnapshotData data, OutputStream out) throws IOException {
    SnapshotCodec.write(data, out);
  }

  @Override
  public CatalogSnapshotData read(String path) {
    try (InputStream in = Files.newInputStream(Path.of(path))) {
      return SnapshotCodec.read(in);
    } catch (IOException e) {
      throw new BeaconException(
          ErrorCode.PARSE_FAILED, "Snapshot file couldn't be read: " + path, "Rescan the store", e);
    }
  }

  @Override
  public void delete(String path) {
    try {
      Files.deleteIfExists(Path.of(path));
    } catch (IOException e) {
      log.warn("[RETENTION] file={} not deleted reason={}", path, e.getMessage());
    }
  }

  private static void deleteQuietly(Path temp) {
    if (temp == null) {
      return;
    }
    try {
      Files.deleteIfExists(temp);
    } catch (IOException e) {
      log.warn("[SNAPSHOT] temp file {} left behind: {}", temp, e.getMessage());
    }
  }
}

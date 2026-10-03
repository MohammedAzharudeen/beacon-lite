package com.beacon.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.testsupport.DemoSnapshots;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GzipFileSnapshotStoreTest {

  @TempDir Path dir;

  @Test
  void writeAtomic_thenRead_sameData() {
    GzipFileSnapshotStore store = new GzipFileSnapshotStore(dir);
    CatalogSnapshotData data = DemoSnapshots.reebok();

    String path = store.writeAtomic(7, data);

    assertThat(path).endsWith("7/2026-10-03T07-31-21Z.json.gz");
    assertThat(store.read(path)).isEqualTo(data);
  }

  @Test
  void writeAtomic_crashMidWrite_leavesNoSnapshotFile() throws IOException {
    GzipFileSnapshotStore crashing =
        new GzipFileSnapshotStore(dir) {
          @Override
          protected void writeData(CatalogSnapshotData data, OutputStream out) throws IOException {
            out.write(new byte[] {31, (byte) 139, 8}); // start of a gzip stream, then the "crash"
            throw new IOException("disk full");
          }
        };

    assertThatThrownBy(() -> crashing.writeAtomic(7, DemoSnapshots.reebok()))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.SNAPSHOT_WRITE_FAILED);
    try (Stream<Path> files = Files.walk(dir)) {
      assertThat(files.filter(Files::isRegularFile)).isEmpty();
    }
  }
}

package com.beacon.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.testsupport.DemoSnapshots;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class SnapshotCodecTest {

  @Test
  void writeThenRead_roundTripsRecordedSnapshot() throws Exception {
    CatalogSnapshotData original = DemoSnapshots.steveMadden();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SnapshotCodec.write(original, out);

    CatalogSnapshotData copy = SnapshotCodec.read(new ByteArrayInputStream(out.toByteArray()));

    assertThat(copy).isEqualTo(original);
  }

  @Test
  void read_unknownSchemaVersion_clearError() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write("{\"schemaVersion\":99}".getBytes());
    }

    assertThatThrownBy(() -> SnapshotCodec.read(new ByteArrayInputStream(out.toByteArray())))
        .isInstanceOf(BeaconException.class)
        .hasMessageContaining("schema version 99");
  }
}

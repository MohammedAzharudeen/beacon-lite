package com.beacon.snapshot;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes raw snapshot files (gzip JSON). Uses its own fixed JSON settings so files stay
 * readable whatever the web layer's settings are. Unknown schema versions are rejected with a clear
 * error instead of being misread.
 */
public final class SnapshotCodec {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private SnapshotCodec() {}

  public static void write(CatalogSnapshotData data, OutputStream out) throws IOException {
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      MAPPER.writeValue(gzip, data);
    }
  }

  public static CatalogSnapshotData read(InputStream in) throws IOException {
    try (GZIPInputStream gzip = new GZIPInputStream(in)) {
      JsonNode tree = MAPPER.readTree(gzip);
      int version = tree.path("schemaVersion").asInt(-1);
      if (version != CatalogSnapshotData.SCHEMA_VERSION) {
        throw new BeaconException(
            ErrorCode.PARSE_FAILED,
            "Snapshot file has schema version "
                + version
                + "; this build reads version "
                + CatalogSnapshotData.SCHEMA_VERSION,
            "Use a snapshot recorded by this version of Beacon Lite",
            null);
      }
      return MAPPER.treeToValue(tree, CatalogSnapshotData.class);
    }
  }
}

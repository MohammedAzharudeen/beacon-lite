package com.beacon.insight;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.insight.report.InsightReport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Stores reports compactly: JSON, gzip-compressed, Base64 text (a full report for a large catalog
 * is several MB of JSON, around a tenth of that compressed).
 */
public final class ReportCodec {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private ReportCodec() {}

  public static String encode(InsightReport report) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
      MAPPER.writeValue(gzip, report);
    } catch (IOException e) {
      throw new BeaconException(
          ErrorCode.INTERNAL_ERROR, "Report couldn't be saved", "Try again", e);
    }
    return Base64.getEncoder().encodeToString(bytes.toByteArray());
  }

  public static InsightReport decode(String payload) {
    try (GZIPInputStream gzip =
        new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(payload)))) {
      return MAPPER.readValue(gzip, InsightReport.class);
    } catch (IOException | IllegalArgumentException e) {
      throw new BeaconException(
          ErrorCode.INTERNAL_ERROR, "Stored report couldn't be read", "Rescan the store", e);
    }
  }
}

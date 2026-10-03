package com.beacon.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Reads recorded store responses from {@code src/test/resources/fixtures}. */
public final class Fixtures {

  private static final Path ROOT = Path.of("src/test/resources/fixtures");

  private Fixtures() {}

  /** A gzip body from {@code <store>/recording/}. */
  public static String recorded(String store, String file) {
    try (InputStream in =
        new GZIPInputStream(
            Files.newInputStream(ROOT.resolve(store).resolve("recording").resolve(file)))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

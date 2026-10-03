package com.beacon.config;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads {@code config/assumptions.yml}: from {@code beacon.assumptions-file} when that file exists
 * (so a merchant can edit it), otherwise the copy packaged in the jar.
 */
@Configuration
public class AssumptionsLoader {

  private static final Logger log = LoggerFactory.getLogger(AssumptionsLoader.class);
  private static final String CLASSPATH_COPY = "/config/assumptions.yml";

  @Bean
  public Assumptions assumptions(BeaconProperties properties) {
    Path file = Path.of(properties.assumptionsFile());
    try {
      if (Files.isRegularFile(file)) {
        log.info("[ASSUMPTIONS] source={}", file.toAbsolutePath());
        return parse(Files.readAllBytes(file));
      }
      try (InputStream in = AssumptionsLoader.class.getResourceAsStream(CLASSPATH_COPY)) {
        if (in == null) {
          throw new IllegalStateException("assumptions.yml not found");
        }
        log.info("[ASSUMPTIONS] source=classpath:{}", CLASSPATH_COPY);
        return parse(in.readAllBytes());
      }
    } catch (IOException e) {
      throw new BeaconException(
          ErrorCode.INTERNAL_ERROR, "assumptions.yml couldn't be read", "Check the file", e);
    }
  }

  /** Parses the YAML and stamps it with the SHA-256 of the exact bytes. */
  public static Assumptions parse(byte[] yaml) throws IOException {
    ObjectMapper mapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    Assumptions parsed = mapper.readValue(yaml, Assumptions.class);
    return parsed.withVersion(sha256(yaml));
  }

  /** Loads the packaged copy; used by tests and the CLI. */
  public static Assumptions packaged() {
    try (InputStream in = AssumptionsLoader.class.getResourceAsStream(CLASSPATH_COPY)) {
      if (in == null) {
        throw new IllegalStateException("Packaged assumptions.yml missing");
      }
      return parse(in.readAllBytes());
    } catch (IOException e) {
      throw new IllegalStateException("Packaged assumptions.yml missing", e);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

package com.beacon.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanCommandTest {

  @TempDir Path out;

  @Test
  void offlineScan_writesMarkdownAndHtmlBrief() throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    String snapshot =
        "src/main/resources/snapshots/www.stevemadden.com/2026-10-03T07-31-21Z.json.gz";

    int exit =
        ScanCommand.run(
            new String[] {"stevemadden.com", "--offline", snapshot, "--out", out.toString()},
            new PrintStream(buffer, true, StandardCharsets.UTF_8));

    String terminal = buffer.toString(StandardCharsets.UTF_8);
    assertThat(exit).isZero();
    assertThat(terminal)
        .contains("HEADLINE  789 products are missing core sizes")
        .contains("TOP ACTIONS")
        .contains("Excluded: 283 pre-order / back-order items");
    String md = Files.readString(out.resolve("stevemadden-insight-brief.md"));
    String html = Files.readString(out.resolve("stevemadden-insight-brief.html"));
    assertThat(md)
        .contains("# Insight Brief · Steve Madden")
        .contains("## Do this")
        .contains("## Expected impact (estimate)");
    assertThat(html).startsWith("<!doctype html>").contains("Estimate").doesNotContain("<script");
  }

  @Test
  void noArguments_usage() {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    int exit =
        ScanCommand.run(new String[] {}, new PrintStream(buffer, true, StandardCharsets.UTF_8));

    assertThat(exit).isEqualTo(2);
    assertThat(buffer.toString(StandardCharsets.UTF_8)).startsWith("Usage:");
  }
}

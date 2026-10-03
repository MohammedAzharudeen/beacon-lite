package com.beacon.cli;

import com.beacon.BeaconApplication;
import com.beacon.adapter.AdapterRegistry;
import com.beacon.adapter.StoreAdapter;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.config.Assumptions;
import com.beacon.fetch.FetchSession;
import com.beacon.fetch.PoliteHttpClient;
import com.beacon.insight.ReportService;
import com.beacon.insight.report.Action;
import com.beacon.insight.report.ExclusionCount;
import com.beacon.insight.report.InsightReport;
import com.beacon.job.Job;
import com.beacon.job.JobRepository;
import com.beacon.job.JobType;
import com.beacon.security.StoreUrl;
import com.beacon.security.UrlGuard;
import com.beacon.snapshot.SnapshotCodec;
import com.beacon.snapshot.SnapshotService;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.store.StoreService;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * {@code java -jar beacon.jar scan <store-url> [--offline <snapshot.json.gz>] [--out <dir>]} . Runs
 * the same services as the web app once, prints a terminal summary and writes {@code
 * reports/<store>-insight-brief.md} and {@code .html}. Uses a throwaway in-memory database so it
 * never touches the app's data. Terminal output is this command's user interface.
 */
public final class ScanCommand {

  private static final Map<String, Object> CLI_PROPERTIES =
      Map.of(
          "spring.datasource.url", "jdbc:h2:mem:beacon-cli;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
          "beacon.seed-demo-stores", "false",
          "beacon.snapshot.scheduler-enabled", "false",
          "beacon.snapshot.dir", System.getProperty("java.io.tmpdir") + "/beacon-cli-snapshots",
          "logging.level.root", "WARN",
          "spring.main.banner-mode", "off");

  private ScanCommand() {}

  private static String[] cliArguments() {
    return CLI_PROPERTIES.entrySet().stream()
        .map(e -> "--" + e.getKey() + "=" + e.getValue())
        .toArray(String[]::new);
  }

  /** Returns the process exit code. */
  public static int run(String[] args, PrintStream out) {
    if (args.length == 0 || args[0].startsWith("--")) {
      out.println(
          "Usage: java -jar beacon.jar scan <store-url> [--offline <snapshot.json.gz>] [--out <dir>]");
      return 2;
    }
    String target = args[0];
    Path offline = option(args, "--offline");
    Path outDir = option(args, "--out") == null ? Path.of("reports") : option(args, "--out");
    try (ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(BeaconApplication.class)
            .web(WebApplicationType.NONE)
            // As command-line arguments so they override application.yml (default properties
            // wouldn't)
            .run(cliArguments())) {
      return scan(ctx, target, offline, outDir, out);
    }
  }

  private static int scan(
      ConfigurableApplicationContext ctx,
      String target,
      Path offline,
      Path outDir,
      PrintStream out) {
    StoreRepository stores = ctx.getBean(StoreRepository.class);
    SnapshotService snapshots = ctx.getBean(SnapshotService.class);
    ReportService reports = ctx.getBean(ReportService.class);
    Assumptions assumptions = ctx.getBean(Assumptions.class);
    NumberFormat n = NumberFormat.getIntegerInstance(Locale.US);
    out.println("Beacon Lite · scanning " + target);
    try {
      Store store;
      if (offline != null) {
        CatalogSnapshotData data;
        try (InputStream in = Files.newInputStream(offline)) {
          data = SnapshotCodec.read(in);
        }
        store =
            stores.save(
                Store.adding(
                    data.store().domain(),
                    StoreService.defaultName(data.store().domain()),
                    data.store().platform()));
        out.println("✓ Recorded snapshot from " + data.capturedAt() + " (offline)");
        snapshots.ingestRecorded(store.getId(), data);
      } else {
        StoreUrl url = ctx.getBean(UrlGuard.class).checkUserInput(target);
        PoliteHttpClient http = ctx.getBean(PoliteHttpClient.class);
        StoreAdapter adapter =
            ctx.getBean(AdapterRegistry.class).detect(new FetchSession(url, http));
        out.println(
            "✓ "
                + label(adapter)
                + " detected · reading the catalog (one request at a time, robots.txt obeyed)…");
        store =
            stores.save(
                Store.adding(url.host(), StoreService.defaultName(url.host()), adapter.platform()));
        Job job =
            ctx.getBean(JobRepository.class).save(Job.queued(store.getId(), JobType.ADD_STORE));
        snapshots.capture(store.getId(), job.getId());
        Job done = ctx.getBean(JobRepository.class).findById(job.getId()).orElseThrow();
        if (done.getErrorCode() != null) {
          out.println("✗ Scan failed: " + done.getErrorCode() + " · " + done.getMessage());
          return 1;
        }
      }
      InsightReport r = reports.require(store.getId());
      Store saved = stores.findById(store.getId()).orElseThrow();
      out.println(
          "✓ "
              + n.format(r.catalog().products())
              + " products · "
              + n.format(r.catalog().variants())
              + " variants read");
      out.println(
          "✓ Store pages checked (home, policies or footer pages, search where allowed, sold-out pages)");
      out.println();
      out.println("HEADLINE  " + r.headline().text());
      out.println("TOP ACTIONS");
      for (Action a : r.topActions()) {
        out.println(
            " "
                + a.rank()
                + ". "
                + a.title()
                + (a.atRiskPerWeek() == null
                    ? ""
                    : "  · est. "
                        + InsightBriefWriter.money(
                            a.atRiskPerWeek().amount(), a.atRiskPerWeek().currency())
                        + "/week"));
      }
      int preBack =
          r.exclusions().stream()
              .filter(
                  e ->
                      e.reason().name().equals("PRE_ORDER")
                          || e.reason().name().equals("BACK_ORDER"))
              .mapToInt(ExclusionCount::count)
              .sum();
      out.println("Excluded: " + preBack + " pre-order / back-order items");
      String name = saved.getDomain().replaceFirst("^www\\.", "").replaceFirst("\\..*$", "");
      Files.createDirectories(outDir);
      Path md = outDir.resolve(name + "-insight-brief.md");
      Path html = outDir.resolve(name + "-insight-brief.html");
      Files.writeString(
          md,
          InsightBriefWriter.markdown(r, saved.getDisplayName(), assumptions),
          StandardCharsets.UTF_8);
      Files.writeString(
          html,
          InsightBriefWriter.html(r, saved.getDisplayName(), assumptions),
          StandardCharsets.UTF_8);
      out.println("Report saved: " + md + " and " + html);
      return 0;
    } catch (BeaconException e) {
      out.println("✗ " + e.getMessage() + " · " + e.hint());
      return 1;
    } catch (IOException e) {
      out.println("✗ Couldn't read or write a file: " + e.getMessage());
      return 1;
    }
  }

  private static String label(StoreAdapter adapter) {
    return switch (adapter.platform()) {
      case SHOPIFY -> "Shopify";
      case GENERIC -> "Generic storefront (sampled)";
      case REPLAY -> "Recorded store";
    };
  }

  private static Path option(String[] args, String name) {
    for (int i = 1; i < args.length - 1; i++) {
      if (args[i].equals(name)) {
        return Path.of(args[i + 1]);
      }
    }
    return null;
  }
}

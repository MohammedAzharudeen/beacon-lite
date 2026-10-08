package com.beacon.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Operational settings from {@code application.yml} under {@code beacon.*}. Judgment calls about
 * the data live in {@code config/assumptions.yml} instead.
 *
 * @param assumptionsFile editable assumptions file; the packaged copy is used when it is missing
 * @param demo true in the {@code demo} profile: recorded snapshots only, no network
 */
@ConfigurationProperties(prefix = "beacon")
public record BeaconProperties(
    @DefaultValue Fetch fetch,
    @DefaultValue Snapshot snapshot,
    @DefaultValue Llm llm,
    @DefaultValue("./config/assumptions.yml") String assumptionsFile,
    @DefaultValue("false") boolean demo) {

  /** Convenience for tests and the CLI. */
  public static BeaconProperties defaults() {
    return new BeaconProperties(
        Fetch.defaults(), Snapshot.defaults(), Llm.defaults(), "./config/assumptions.yml", false);
  }

  /**
   * How the app talks to stores.
   *
   * @param userAgent sent with every request so stores can identify the tool
   * @param delay pause between two requests to the same store
   * @param connectTimeout time allowed to open a connection
   * @param readTimeout time allowed for a full response
   * @param maxAttempts attempts per request, including the first
   * @param maxResponseBytes larger responses are aborted
   * @param maxRedirects redirects followed per request
   * @param maxRetryAfter upper bound on a store's Retry-After request
   * @param allowPrivateHosts only for tests against a local stub server; never enable in real use
   */
  public record Fetch(
      @DefaultValue("BeaconLite/1.0 (+contact in README)") String userAgent,
      @DefaultValue("500ms") Duration delay,
      @DefaultValue("10s") Duration connectTimeout,
      @DefaultValue("30s") Duration readTimeout,
      @DefaultValue("3") int maxAttempts,
      @DefaultValue("20971520") long maxResponseBytes,
      @DefaultValue("5") int maxRedirects,
      @DefaultValue("30s") Duration maxRetryAfter,
      @DefaultValue("false") boolean allowPrivateHosts) {

    public static Fetch defaults() {
      return new Fetch(
          "BeaconLite/1.0 (+contact in README)",
          Duration.ofMillis(500),
          Duration.ofSeconds(10),
          Duration.ofSeconds(30),
          3,
          20_971_520,
          5,
          Duration.ofSeconds(30),
          false);
    }
  }

  /**
   * Snapshot schedule and storage.
   *
   * @param interval time between automatic re-checks of every store
   * @param dir where raw snapshot files are written
   * @param keepAllDays raw snapshots younger than this are all kept; older ones one per day
   * @param schedulerEnabled turns the automatic re-check off (tests, CLI)
   */
  public record Snapshot(
      @DefaultValue("6h") Duration interval,
      @DefaultValue("./data/live/snapshots") String dir,
      @DefaultValue("7") int keepAllDays,
      @DefaultValue("true") boolean schedulerEnabled) {

    public static Snapshot defaults() {
      return new Snapshot(Duration.ofHours(6), "./data/live/snapshots", 7, true);
    }
  }

  /**
   * AI model connection (any OpenAI-format chat completions endpoint).
   *
   * @param baseUrl e.g. Ollama's {@code http://localhost:11434/v1}
   * @param model model name
   * @param apiKey optional; empty for Ollama, never logged
   * @param timeout per model call; slower calls fall back to rule-based answers
   * @param maxToolCalls tool calls allowed per question
   */
  public record Llm(
      @DefaultValue("ollama") String provider,
      @DefaultValue("http://localhost:11434/v1") String baseUrl,
      @DefaultValue("qwen2.5:7b") String model,
      @DefaultValue("") String apiKey,
      @DefaultValue("60s") Duration timeout,
      @DefaultValue("4") int maxToolCalls) {

    public static Llm defaults() {
      return new Llm(
          "ollama", "http://localhost:11434/v1", "qwen2.5:7b", "", Duration.ofSeconds(60), 4);
    }

    @Override
    public String toString() {
      return "Llm[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model + "]";
    }
  }
}

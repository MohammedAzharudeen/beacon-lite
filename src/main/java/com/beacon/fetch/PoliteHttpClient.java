package com.beacon.fetch;

import com.beacon.adapter.model.RobotsInfo;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.common.Metrics;
import com.beacon.config.BeaconProperties;
import com.beacon.robots.RobotsDecision;
import com.beacon.robots.RobotsRules;
import com.beacon.security.StoreUrl;
import com.beacon.security.UrlGuard;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only way the app talks to stores. Every request is checked against the URL guard and the
 * store's robots.txt, sent one at a time per store with a pause in between, retried with backoff on
 * network errors and 5xx, slowed down on 429, and capped in size.
 */
@Component
public class PoliteHttpClient {

  private static final Logger log = LoggerFactory.getLogger(PoliteHttpClient.class);
  private static final String ROBOTS_PATH = "/robots.txt";
  private static final String PRODUCT_TOKEN = "BeaconLite";

  private final BeaconProperties.Fetch config;
  private final UrlGuard urlGuard;
  private final HttpClient http;
  private final HostRateLimiter limiter;
  private final RobotsCache robotsCache = new RobotsCache();
  private final Metrics metrics;

  public PoliteHttpClient(BeaconProperties properties, UrlGuard urlGuard, Metrics metrics) {
    this.config = properties.fetch();
    this.urlGuard = urlGuard;
    this.metrics = metrics;
    this.limiter = new HostRateLimiter(config.delay());
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER) // redirects are checked by hand
            .build();
  }

  /** Fetches a path on a store, obeying its robots.txt. */
  public FetchResult get(StoreUrl store, String pathAndQuery) {
    return get(store.resolve(pathAndQuery));
  }

  /** Fetches an absolute URL, obeying the target host's robots.txt. */
  public FetchResult get(URI uri) {
    URI current = uri;
    for (int redirect = 0; redirect <= config.maxRedirects(); redirect++) {
      urlGuard.checkTarget(current);
      RobotsDecision decision = robotsFor(current).rules().decide(pathAndQuery(current));
      if (decision.disallowed()) {
        log.info("[FETCH] url={} robots=DISALLOWED rule=\"{}\"", current, decision.rule());
        metrics.increment("fetch.requests.blocked_by_robots");
        return FetchResult.blocked(current, decision.rule());
      }
      RawResponse response = sendWithRetries(current);
      if (response.isRedirect()) {
        current = current.resolve(response.location());
        continue;
      }
      return response.toResult(current);
    }
    throw new BeaconException(
        ErrorCode.STORE_NOT_REACHABLE, "Too many redirects", "Check the store address", null);
  }

  /** Forgets cached robots.txt rules for a host so the next scan reads the latest file. */
  public void refreshRobots(String host) {
    robotsCache.invalidate(host);
  }

  /** Whether the store's robots.txt was read, missing or unavailable (loads it if needed). */
  public RobotsInfo.State robotsState(StoreUrl store) {
    return robotsFor(store.baseUri()).state();
  }

  /** Sitemap URLs from the store's robots.txt (loads it if needed). */
  public List<String> sitemaps(StoreUrl store) {
    return robotsFor(store.baseUri()).rules().sitemaps();
  }

  private RobotsCache.Entry robotsFor(URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    RobotsCache.Entry cached = robotsCache.get(host);
    if (cached != null) {
      return cached;
    }
    urlGuard.checkTarget(uri.resolve(ROBOTS_PATH));
    RobotsCache.Entry entry = loadRobots(uri.resolve(ROBOTS_PATH));
    robotsCache.put(host, entry);
    return entry;
  }

  private RobotsCache.Entry loadRobots(URI robotsUri) {
    try {
      RawResponse response = sendWithRetries(robotsUri);
      if (response.status >= 200 && response.status < 300 && response.body != null) {
        log.info("[ROBOTS] url={} loaded bytes={}", robotsUri, response.bytes);
        return new RobotsCache.Entry(
            RobotsRules.parse(response.body, PRODUCT_TOKEN), RobotsInfo.State.LOADED);
      }
      if (response.status >= 400 && response.status < 500) {
        log.info("[ROBOTS] url={} status={} → all allowed", robotsUri, response.status);
        return new RobotsCache.Entry(RobotsRules.allowAll(), RobotsInfo.State.MISSING);
      }
      log.warn("[ROBOTS] url={} status={} → nothing fetched", robotsUri, response.status);
      return new RobotsCache.Entry(RobotsRules.disallowAll(), RobotsInfo.State.UNAVAILABLE);
    } catch (BeaconException e) {
      log.warn("[ROBOTS] url={} unreadable code={} → nothing fetched", robotsUri, e.code());
      return new RobotsCache.Entry(RobotsRules.disallowAll(), RobotsInfo.State.UNAVAILABLE);
    }
  }

  private RawResponse sendWithRetries(URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    boolean sawRateLimit = false;
    for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
      try {
        RawResponse response = sendOnce(host, uri);
        if (response.status == 429) {
          sawRateLimit = true;
          Duration wait = response.retryAfter.orElse(backoff(attempt));
          log.warn("[FETCH] url={} status=429 wait={} attempt={}", uri, wait, attempt);
          sleep(min(wait, config.maxRetryAfter()));
          continue;
        }
        if (response.status >= 500 && attempt < config.maxAttempts()) {
          log.warn("[FETCH] url={} status={} attempt={}", uri, response.status, attempt);
          sleep(backoff(attempt));
          continue;
        }
        return response;
      } catch (IOException e) {
        metrics.increment("fetch.requests.failed");
        log.warn("[FETCH] url={} error={} attempt={}", uri, e.getClass().getSimpleName(), attempt);
        if (attempt < config.maxAttempts()) {
          sleep(backoff(attempt));
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BeaconException(ErrorCode.INTERNAL_ERROR, "Interrupted", "Try again", e);
      }
    }
    throw new BeaconException(
        sawRateLimit ? ErrorCode.STORE_RATE_LIMITED : ErrorCode.STORE_NOT_REACHABLE);
  }

  private RawResponse sendOnce(String host, URI uri) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(config.readTimeout())
            .header("User-Agent", config.userAgent())
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .GET()
            .build();
    limiter.acquire(host);
    long started = System.nanoTime();
    try {
      HttpResponse<InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream in = response.body()) {
        byte[] body =
            in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, config.maxResponseBytes() + 1));
        long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
        boolean tooLarge = body.length > config.maxResponseBytes();
        metrics.increment("fetch.requests.sent");
        metrics.record("fetch.duration_ms", ms);
        if (response.statusCode() >= 400) {
          metrics.increment("fetch.requests.http_error");
        }
        log.info(
            "[FETCH] url={} status={} bytes={} ms={}{}",
            uri,
            response.statusCode(),
            body.length,
            ms,
            tooLarge ? " TOO_LARGE" : "");
        return new RawResponse(
            response.statusCode(),
            response.headers().firstValue("Content-Type").orElse(null),
            response.headers().firstValue("Location").orElse(null),
            parseRetryAfter(response.headers().firstValue("Retry-After").orElse(null)),
            tooLarge ? null : new String(body, StandardCharsets.UTF_8),
            body.length,
            ms,
            tooLarge);
      }
    } finally {
      limiter.release(host);
    }
  }

  static Optional<Duration> parseRetryAfter(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String v = value.trim();
    if (v.chars().allMatch(Character::isDigit)) {
      return Optional.of(Duration.ofSeconds(Long.parseLong(v)));
    }
    try {
      ZonedDateTime when = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
      Duration wait = Duration.between(ZonedDateTime.now(when.getZone()), when);
      return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /** Doubles the configured pause on each attempt: delay, 2×delay, 4×delay… */
  private Duration backoff(int attempt) {
    return config.delay().multipliedBy(1L << (attempt - 1));
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BeaconException(ErrorCode.INTERNAL_ERROR, "Interrupted", "Try again", e);
    }
  }

  private static String pathAndQuery(URI uri) {
    String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
  }

  private record RawResponse(
      int status,
      String contentType,
      String location,
      Optional<Duration> retryAfter,
      String body,
      long bytes,
      long durationMs,
      boolean tooLarge) {

    boolean isRedirect() {
      return status >= 300 && status < 400 && location != null;
    }

    FetchResult toResult(URI uri) {
      if (tooLarge) {
        return FetchResult.tooLarge(uri, status, bytes, durationMs);
      }
      return new FetchResult(
          FetchOutcome.RESPONSE, uri, status, contentType, body, bytes, durationMs, null);
    }
  }
}

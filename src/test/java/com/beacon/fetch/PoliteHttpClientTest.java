package com.beacon.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.common.Metrics;
import com.beacon.config.BeaconProperties;
import com.beacon.security.StoreUrl;
import com.beacon.security.UrlGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the client against a local stub server; no live network. */
class PoliteHttpClientTest {

  private static final String ROBOTS = "User-agent: *\nDisallow: /collections/*sort_by*\n";

  private HttpServer server;
  private StoreUrl store;
  private PoliteHttpClient client;
  private final List<String> requested = new CopyOnWriteArrayList<>();
  private final Map<String, Handler> routes = new ConcurrentHashMap<>();

  @FunctionalInterface
  private interface Handler {
    void handle(HttpExchange exchange, int hit) throws IOException;
  }

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getRawPath();
          String full =
              exchange.getRequestURI().getRawQuery() == null
                  ? path
                  : path + "?" + exchange.getRequestURI().getRawQuery();
          requested.add(full);
          int hit = hits.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
          Handler handler = routes.getOrDefault(path, (ex, n) -> send(ex, 404, "text/plain", ""));
          try {
            handler.handle(exchange, hit);
          } finally {
            exchange.close();
          }
        });
    server.start();
    URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    store = new StoreUrl("127.0.0.1", base);
    routes.put("/robots.txt", (ex, n) -> send(ex, 200, "text/plain", ROBOTS));
    client =
        new PoliteHttpClient(
            properties(Duration.ofMillis(300)), new StubAwareGuard(), new Metrics());
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void get_allowedPath_returnsBody() {
    routes.put("/products.json", (ex, n) -> send(ex, 200, "application/json", "{\"products\":[]}"));

    FetchResult result = client.get(store, "/products.json?limit=250&page=1");

    assertThat(result.isOk()).isTrue();
    assertThat(result.isJson()).isTrue();
    assertThat(result.body()).isEqualTo("{\"products\":[]}");
    assertThat(requested).containsExactly("/robots.txt", "/products.json?limit=250&page=1");
  }

  @Test
  void get_robotsDisallowedPath_isNeverRequested() {
    FetchResult result = client.get(store, "/collections/all?sort_by=best-selling");

    assertThat(result.outcome()).isEqualTo(FetchOutcome.BLOCKED_BY_ROBOTS);
    assertThat(result.robotsRule()).contains("sort_by");
    assertThat(requested).containsExactly("/robots.txt");
  }

  @Test
  void get_robotsFileReadOncePerHost_untilRefreshed() {
    routes.put("/a", (ex, n) -> send(ex, 200, "text/plain", "a"));

    client.get(store, "/a");
    client.get(store, "/a");
    client.refreshRobots("127.0.0.1");
    client.get(store, "/a");

    assertThat(requested).containsExactly("/robots.txt", "/a", "/a", "/robots.txt", "/a");
  }

  @Test
  void get_robotsMissing404_allowsEverything() {
    routes.remove("/robots.txt");
    routes.put("/collections/all", (ex, n) -> send(ex, 200, "text/html", "<html></html>"));

    assertThat(client.get(store, "/collections/all?sort_by=best-selling").isOk()).isTrue();
  }

  @Test
  void get_robotsServerError_fetchesNothing() {
    routes.put("/robots.txt", (ex, n) -> send(ex, 503, "text/plain", ""));

    FetchResult result = client.get(store, "/products.json");

    assertThat(result.outcome()).isEqualTo(FetchOutcome.BLOCKED_BY_ROBOTS);
    assertThat(requested).doesNotContain("/products.json");
  }

  @Test
  void get_rateLimitedOnce_waitsAndRetries() {
    routes.put(
        "/products.json",
        (ex, n) -> {
          if (n == 1) {
            ex.getResponseHeaders().add("Retry-After", "0");
            send(ex, 429, "text/plain", "slow down");
          } else {
            send(ex, 200, "application/json", "{}");
          }
        });

    assertThat(client.get(store, "/products.json").isOk()).isTrue();
    assertThat(requested).filteredOn("/products.json"::equals).hasSize(2);
  }

  @Test
  void get_alwaysRateLimited_throwsStoreRateLimited() {
    routes.put(
        "/products.json",
        (ex, n) -> {
          ex.getResponseHeaders().add("Retry-After", "0");
          send(ex, 429, "text/plain", "");
        });

    assertThatThrownBy(() -> client.get(store, "/products.json"))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.STORE_RATE_LIMITED);
    assertThat(requested).filteredOn("/products.json"::equals).hasSize(3);
  }

  @Test
  void get_serverErrorThenSuccess_retries() {
    routes.put(
        "/products.json",
        (ex, n) -> send(ex, n == 1 ? 503 : 200, "application/json", n == 1 ? "" : "{}"));

    assertThat(client.get(store, "/products.json").isOk()).isTrue();
  }

  @Test
  void get_serverErrorEveryAttempt_returnsLastResponse() {
    routes.put("/products.json", (ex, n) -> send(ex, 502, "text/plain", ""));

    FetchResult result = client.get(store, "/products.json");

    assertThat(result.status()).isEqualTo(502);
    assertThat(result.isOk()).isFalse();
    assertThat(requested).filteredOn("/products.json"::equals).hasSize(3);
  }

  @Test
  void get_slowResponse_timesOutAndThrowsNotReachable() {
    routes.put(
        "/products.json",
        (ex, n) -> {
          sleep(Duration.ofMillis(1500));
          send(ex, 200, "application/json", "{}");
        });

    assertThatThrownBy(() -> client.get(store, "/products.json"))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.STORE_NOT_REACHABLE);
  }

  @Test
  void get_oversizedResponse_isAbortedNotReturned() {
    routes.put("/products.json", (ex, n) -> send(ex, 200, "application/json", "x".repeat(5000)));

    FetchResult result = client.get(store, "/products.json");

    assertThat(result.outcome()).isEqualTo(FetchOutcome.TOO_LARGE);
    assertThat(result.body()).isNull();
  }

  @Test
  void get_htmlInsteadOfJson_isNotTreatedAsJson() {
    routes.put("/products.json", (ex, n) -> send(ex, 200, "text/html", "<!doctype html>"));

    FetchResult result = client.get(store, "/products.json");

    assertThat(result.isOk()).isTrue();
    assertThat(result.isJson()).isFalse();
  }

  @Test
  void get_redirectOnSameStore_isFollowedAndRobotsChecked() {
    routes.put("/old", (ex, n) -> redirect(ex, "/products.json"));
    routes.put("/products.json", (ex, n) -> send(ex, 200, "application/json", "{}"));

    FetchResult result = client.get(store, "/old");

    assertThat(result.isOk()).isTrue();
    assertThat(result.uri().getPath()).isEqualTo("/products.json");
  }

  @Test
  void get_redirectToDisallowedPath_isNotFollowed() {
    routes.put("/old", (ex, n) -> redirect(ex, "/collections/all?sort_by=price"));

    FetchResult result = client.get(store, "/old");

    assertThat(result.outcome()).isEqualTo(FetchOutcome.BLOCKED_BY_ROBOTS);
    assertThat(requested).noneMatch(p -> p.startsWith("/collections"));
  }

  @Test
  void get_redirectToPrivateAddress_isRejected() {
    routes.put("/old", (ex, n) -> redirect(ex, "http://169.254.169.254/latest/meta-data/"));

    assertThatThrownBy(() -> client.get(store, "/old"))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.URL_NOT_ALLOWED);
  }

  @Test
  void get_redirectLoop_stopsAfterLimit() {
    routes.put("/loop", (ex, n) -> redirect(ex, "/loop"));

    assertThatThrownBy(() -> client.get(store, "/loop"))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.STORE_NOT_REACHABLE);
  }

  @Test
  void get_sendsIdentifyingUserAgent() {
    List<String> agents = new CopyOnWriteArrayList<>();
    routes.put(
        "/a",
        (ex, n) -> {
          agents.add(ex.getRequestHeaders().getFirst("User-Agent"));
          send(ex, 200, "text/plain", "a");
        });

    client.get(store, "/a");

    assertThat(agents).containsExactly("BeaconLite/test");
  }

  @Test
  void parseRetryAfter_secondsAndDates() {
    assertThat(PoliteHttpClient.parseRetryAfter("7")).contains(Duration.ofSeconds(7));
    assertThat(PoliteHttpClient.parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT"))
        .contains(Duration.ZERO);
    assertThat(PoliteHttpClient.parseRetryAfter("soon")).isEmpty();
    assertThat(PoliteHttpClient.parseRetryAfter(null)).isEmpty();
  }

  private static BeaconProperties properties(Duration readTimeout) {
    return new BeaconProperties(
        new BeaconProperties.Fetch(
            "BeaconLite/test",
            Duration.ofMillis(10),
            Duration.ofSeconds(2),
            readTimeout,
            3,
            1000,
            5,
            Duration.ofSeconds(1),
            true),
        BeaconProperties.Snapshot.defaults(),
        BeaconProperties.Llm.defaults(),
        "./config/assumptions.yml",
        false);
  }

  private static void send(HttpExchange ex, int status, String type, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", type);
    ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = ex.getResponseBody()) {
        out.write(bytes);
      }
    }
  }

  private static void redirect(HttpExchange ex, String location) throws IOException {
    ex.getResponseHeaders().add("Location", location);
    ex.sendResponseHeaders(302, -1);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Lets requests reach the loopback stub server but applies the real private-address checks to
   * every other host, so redirect protection is tested as in production.
   */
  private static final class StubAwareGuard extends UrlGuard {
    private final UrlGuard strict =
        new UrlGuard(host -> List.of(InetAddress.getByName(host)), false);

    StubAwareGuard() {
      super(host -> List.of(InetAddress.getByName(host)), true);
    }

    @Override
    public void checkTarget(URI uri) {
      if ("127.0.0.1".equals(uri.getHost())) {
        super.checkTarget(uri);
      } else {
        strict.checkTarget(uri);
      }
    }
  }
}

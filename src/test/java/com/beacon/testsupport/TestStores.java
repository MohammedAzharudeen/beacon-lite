package com.beacon.testsupport;

import com.beacon.common.Metrics;
import com.beacon.config.BeaconProperties;
import com.beacon.fetch.FetchSession;
import com.beacon.fetch.PoliteHttpClient;
import com.beacon.security.StoreUrl;
import com.beacon.security.UrlGuard;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Wiring for tests that read a recorded store through the real polite client. */
public final class TestStores {

  private TestStores() {}

  /** A fetch session against a local server, with fast settings and the loopback guard relaxed. */
  public static FetchSession session(URI base) {
    BeaconProperties.Fetch fetch =
        new BeaconProperties.Fetch(
            "BeaconLite/test",
            Duration.ZERO,
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            1,
            50_000_000,
            5,
            Duration.ofSeconds(1),
            true);
    BeaconProperties props =
        new BeaconProperties(
            fetch,
            BeaconProperties.Snapshot.defaults(),
            BeaconProperties.Llm.defaults(),
            "./config/assumptions.yml",
            false);
    UrlGuard guard = new UrlGuard(host -> List.of(InetAddress.getByName(host)), true);
    PoliteHttpClient http = new PoliteHttpClient(props, guard, new Metrics());
    return new FetchSession(new StoreUrl(base.getHost(), base), http);
  }
}

package com.beacon.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

  private static final Map<String, String> DNS =
      Map.of(
          "www.stevemadden.com", "23.227.38.74",
          "internal.example", "10.0.0.5",
          "metadata.example", "169.254.169.254");

  private final UrlGuard guard = new UrlGuard(UrlGuardTest::fakeResolve, false);

  @Test
  void checkUserInput_bareDomain_addsHttpsAndDropsPath() {
    StoreUrl url = guard.checkUserInput("  WWW.SteveMadden.com/collections/boots?x=1 ");

    assertThat(url.host()).isEqualTo("www.stevemadden.com");
    assertThat(url.baseUri()).isEqualTo(URI.create("https://www.stevemadden.com"));
    assertThat(url.resolve("/products.json?page=2"))
        .isEqualTo(URI.create("https://www.stevemadden.com/products.json?page=2"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://internal.example",
        "https://metadata.example",
        "http://127.0.0.1",
        "http://[::1]",
        "http://192.168.1.10",
        "http://100.64.0.1"
      })
  void checkUserInput_nonPublicAddress_rejected(String raw) {
    assertThatThrownBy(() -> guard.checkUserInput(raw))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.URL_NOT_ALLOWED);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://www.stevemadden.com/x",
        "https://user:pw@www.stevemadden.com/",
        "https://www.stevemadden.com:8443/"
      })
  void checkTarget_unsafeUrlShape_rejected(String raw) {
    assertThatThrownBy(() -> guard.checkTarget(URI.create(raw)))
        .isInstanceOf(BeaconException.class);
  }

  @Test
  void checkTarget_publicHttps_accepted() {
    guard.checkTarget(URI.create("https://www.stevemadden.com/products.json"));
  }

  @Test
  void checkUserInput_unknownHost_reportsNotReachable() {
    assertThatThrownBy(() -> guard.checkUserInput("no-such-store.example"))
        .isInstanceOf(BeaconException.class)
        .extracting(e -> ((BeaconException) e).code())
        .isEqualTo(ErrorCode.STORE_NOT_REACHABLE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.1.2.3", "192.0.0.8", "240.0.0.1", "fd00::1", "224.0.0.1"})
  void isNonPublic_reservedRanges_true(String ip) throws UnknownHostException {
    assertThat(UrlGuard.isNonPublic(InetAddress.getByName(ip))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"23.227.38.74", "8.8.8.8", "2606:4700::1"})
  void isNonPublic_publicAddresses_false(String ip) throws UnknownHostException {
    assertThat(UrlGuard.isNonPublic(InetAddress.getByName(ip))).isFalse();
  }

  private static List<InetAddress> fakeResolve(String host) throws UnknownHostException {
    String ip = DNS.get(host);
    if (ip != null) {
      return List.of(InetAddress.getByName(ip));
    }
    if (host.matches("[0-9.]+|\\[?[0-9a-f:]+\\]?")) {
      return List.of(InetAddress.getByName(host.replace("[", "").replace("]", "")));
    }
    throw new UnknownHostException(host);
  }
}

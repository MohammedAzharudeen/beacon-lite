package com.beacon.security;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.BeaconProperties;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stops the server from being used to reach internal addresses (SSRF). Every user-typed URL and
 * every redirect target passes through here before any request is sent.
 */
@Component
public class UrlGuard {

  private final HostResolver resolver;
  private final boolean allowPrivateHosts;

  @Autowired
  public UrlGuard(BeaconProperties properties) {
    this(HostResolver.system(), properties.fetch().allowPrivateHosts());
  }

  public UrlGuard(HostResolver resolver, boolean allowPrivateHosts) {
    this.resolver = resolver;
    this.allowPrivateHosts = allowPrivateHosts;
  }

  /**
   * Normalises what a user typed ("stevemadden.com", "https://www.reebok.com/collections/x") into a
   * store address and checks it is safe to fetch.
   *
   * @throws BeaconException INVALID_URL if it isn't a web address, URL_NOT_ALLOWED if it points at
   *     a private or reserved network
   */
  public StoreUrl checkUserInput(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BeaconException(ErrorCode.INVALID_URL);
    }
    String trimmed = raw.trim();
    String withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed;
    URI uri = parse(withScheme);
    checkTarget(uri);
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    String base = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + portSuffix(uri);
    return new StoreUrl(host, URI.create(base));
  }

  /**
   * Checks a full URL that is about to be requested (including redirect targets).
   *
   * @throws BeaconException INVALID_URL or URL_NOT_ALLOWED
   */
  public void checkTarget(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new BeaconException(ErrorCode.INVALID_URL);
    }
    if (uri.getHost() == null || uri.getRawUserInfo() != null) {
      throw new BeaconException(ErrorCode.INVALID_URL);
    }
    if (allowPrivateHosts) {
      return;
    }
    int port = uri.getPort();
    if (port != -1 && port != 80 && port != 443) {
      throw new BeaconException(ErrorCode.URL_NOT_ALLOWED);
    }
    for (InetAddress address : resolve(uri.getHost().toLowerCase(Locale.ROOT))) {
      if (isNonPublic(address)) {
        throw new BeaconException(ErrorCode.URL_NOT_ALLOWED);
      }
    }
  }

  private URI parse(String value) {
    try {
      URI uri = new URI(value);
      if (uri.getHost() == null) {
        throw new BeaconException(ErrorCode.INVALID_URL);
      }
      return uri;
    } catch (URISyntaxException e) {
      throw new BeaconException(ErrorCode.INVALID_URL);
    }
  }

  private List<InetAddress> resolve(String host) {
    try {
      List<InetAddress> addresses = resolver.resolve(host);
      if (addresses.isEmpty()) {
        throw new BeaconException(ErrorCode.STORE_NOT_REACHABLE);
      }
      return addresses;
    } catch (UnknownHostException e) {
      throw new BeaconException(
          ErrorCode.STORE_NOT_REACHABLE,
          "We couldn't find that address",
          "Check the spelling, e.g. stevemadden.com",
          e);
    }
  }

  /**
   * True for loopback, private, link-local (incl. cloud metadata), multicast and reserved ranges.
   */
  static boolean isNonPublic(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] b = address.getAddress();
    if (address instanceof Inet6Address) {
      return (b[0] & 0xfe) == 0xfc; // fc00::/7 unique local
    }
    int first = b[0] & 0xff;
    int second = b[1] & 0xff;
    return first == 0 // 0.0.0.0/8
        || (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 carrier-grade NAT
        || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24 IETF reserved
        || first >= 240; // 240.0.0.0/4 reserved and broadcast
  }

  private static String portSuffix(URI uri) {
    return uri.getPort() == -1 ? "" : ":" + uri.getPort();
  }
}

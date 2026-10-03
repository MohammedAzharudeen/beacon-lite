package com.beacon.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Resolves a host name to its addresses; replaceable in tests so no real DNS is needed. */
@FunctionalInterface
public interface HostResolver {

  List<InetAddress> resolve(String host) throws UnknownHostException;

  static HostResolver system() {
    return host -> List.of(InetAddress.getAllByName(host));
  }
}

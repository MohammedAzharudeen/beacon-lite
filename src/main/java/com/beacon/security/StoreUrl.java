package com.beacon.security;

import java.net.URI;

/**
 * A validated store address.
 *
 * @param host lower-cased host name, e.g. {@code www.stevemadden.com}
 * @param baseUri scheme and host only, e.g. {@code https://www.stevemadden.com}
 */
public record StoreUrl(String host, URI baseUri) {

  /** Resolves a path such as {@code /products.json?page=2} against the store's base address. */
  public URI resolve(String pathAndQuery) {
    return baseUri.resolve(pathAndQuery);
  }
}

package com.beacon.adapter.model;

import com.beacon.adapter.Platform;

/**
 * Store identity inside a snapshot.
 *
 * @param currency ISO code read from the storefront, or {@code null} when not found
 */
public record StoreInfo(String domain, Platform platform, String currency) {}

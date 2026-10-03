package com.beacon.adapter;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.Assumptions;
import com.beacon.config.BeaconProperties;
import com.beacon.fetch.FetchResult;
import com.beacon.fetch.FetchSession;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Picks the adapter for a store. */
@Component
public class AdapterRegistry {

  private static final Logger log = LoggerFactory.getLogger(AdapterRegistry.class);

  private final ShopifyAdapter shopify;
  private final GenericAdapter generic;
  private final ReplayAdapter replay;
  private final boolean demo;
  private final Pattern shopifyMarkers;

  public AdapterRegistry(
      ShopifyAdapter shopify,
      GenericAdapter generic,
      ReplayAdapter replay,
      BeaconProperties properties,
      Assumptions assumptions) {
    this.shopify = shopify;
    this.generic = generic;
    this.replay = replay;
    this.demo = properties.demo();
    this.shopifyMarkers =
        Pattern.compile(assumptions.detection().shopifyMarkers(), Pattern.CASE_INSENSITIVE);
  }

  /**
   * Detects the platform. Shopify first (feed check), then the generic reader; a Shopify site
   * without a public feed (headless) gets its own message.
   */
  public StoreAdapter detect(FetchSession session) {
    if (demo) {
      if (replay.supports(session)) {
        return replay;
      }
      throw new BeaconException(
          ErrorCode.PLATFORM_NOT_SUPPORTED,
          "Demo mode only has the recorded demo stores",
          "Start without the demo profile to scan live stores",
          null);
    }
    ShopifyAdapter.FeedProbe probe = shopify.probe(session);
    if (probe == ShopifyAdapter.FeedProbe.FEED) {
      log.info("[DETECT] store={} platform=SHOPIFY", session.store().host());
      return shopify;
    }
    if (probe == ShopifyAdapter.FeedProbe.BOT_PROTECTION) {
      throw new BeaconException(ErrorCode.STORE_BLOCKS_AUTOMATION);
    }
    boolean headlessShopify = looksLikeShopify(session);
    if (generic.supports(session)) {
      log.info("[DETECT] store={} platform=GENERIC", session.store().host());
      return generic;
    }
    log.info(
        "[DETECT] store={} unsupported headlessShopify={}",
        session.store().host(),
        headlessShopify);
    throw new BeaconException(
        headlessShopify ? ErrorCode.CATALOG_FEED_UNAVAILABLE : ErrorCode.PLATFORM_NOT_SUPPORTED);
  }

  /** The adapter for an already-detected platform. */
  public StoreAdapter forPlatform(Platform platform) {
    if (demo) {
      return replay;
    }
    return switch (platform) {
      case SHOPIFY -> shopify;
      case GENERIC -> generic;
      case REPLAY -> replay;
    };
  }

  private boolean looksLikeShopify(FetchSession session) {
    try {
      FetchResult home = session.get("/");
      return home.isOk() && shopifyMarkers.matcher(home.body()).find();
    } catch (BeaconException e) {
      return false;
    }
  }
}

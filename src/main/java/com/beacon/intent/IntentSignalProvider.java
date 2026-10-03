package com.beacon.intent;

import com.beacon.insight.DemandSignal;
import com.beacon.insight.ProductView;
import com.beacon.insight.StoreView;
import java.util.Set;

/**
 * Where demand signals come from. Today: public stand-ins read from the storefront. With Swym, an
 * implementation backed by wishlist adds and back-in-stock signups plugs in here, and $ at risk
 * becomes measured instead of estimated.
 */
public interface IntentSignalProvider {

  /** Demand signals present for a product. */
  Set<DemandSignal> signals(ProductView product, StoreView store);

  /** Signals this provider can produce for the store at all (e.g. no sell-out speed on day one). */
  Set<DemandSignal> available(StoreView store);
}

package com.beacon.intent;

import com.beacon.adapter.model.VariantData;
import com.beacon.config.Assumptions;
import com.beacon.insight.DemandSignal;
import com.beacon.insight.ProductView;
import com.beacon.insight.StoreView;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Demand signals from public storefront data only, each standing in for a Swym intent signal. The
 * store's best-selling sort order is never used (robots.txt blocks it); only the store's own
 * best-seller collections count as "best seller".
 */
public final class PublicStandInProvider implements IntentSignalProvider {

  /** Public stand-in → the Swym signal it would become. */
  public static final Map<DemandSignal, String> SWYM_SIGNAL =
      Map.of(
          DemandSignal.BEST_SELLER_COLLECTION, "Best-seller collection → wishlist adds per product",
          DemandSignal.PROMOTED, "Promoted by the store → wishlist adds per product",
          DemandSignal.SELL_OUT_SPEED,
              "Sell-out speed between checks → back-in-stock signups per SKU",
          DemandSignal.FULL_PRICE, "Full-price sell-through → wishlist-to-purchase conversion",
          DemandSignal.RECENTLY_LAUNCHED, "Recently launched → early wishlist adds");

  private final Assumptions.Demand demand;

  public PublicStandInProvider(Assumptions.Demand demand) {
    this.demand = demand;
  }

  @Override
  public Set<DemandSignal> signals(ProductView view, StoreView store) {
    Set<DemandSignal> signals = EnumSet.noneOf(DemandSignal.class);
    if (view.inBestSellerCollection()) {
      signals.add(DemandSignal.BEST_SELLER_COLLECTION);
    }
    if (view.promoted() && !view.inBestSellerCollection()) {
      // best-seller products are in the promoted set too; counting both would double the signal
      signals.add(DemandSignal.PROMOTED);
    }
    if (store.hasPreviousSnapshot()
        && view.product().variants().stream()
            .anyMatch(v -> store.soldOutSinceLastCheck().contains(v.externalId()))) {
      signals.add(DemandSignal.SELL_OUT_SPEED);
    }
    boolean anySoldOut = view.product().variants().stream().anyMatch(v -> !v.available());
    boolean discounted =
        view.product().variants().stream().anyMatch(PublicStandInProvider::discounted);
    if (anySoldOut && !discounted) {
      signals.add(DemandSignal.FULL_PRICE);
    }
    Instant published = view.product().publishedAt();
    if (published != null
        && Duration.between(published, store.data().capturedAt()).toDays()
            <= demand.recentlyLaunchedDays()
        && !published.isAfter(store.data().capturedAt())) {
      signals.add(DemandSignal.RECENTLY_LAUNCHED);
    }
    return signals;
  }

  @Override
  public Set<DemandSignal> available(StoreView store) {
    Set<DemandSignal> available =
        EnumSet.of(DemandSignal.PROMOTED, DemandSignal.FULL_PRICE, DemandSignal.RECENTLY_LAUNCHED);
    if (store.hasBestSellerCollection()) {
      available.add(DemandSignal.BEST_SELLER_COLLECTION);
    }
    if (store.hasPreviousSnapshot()) {
      available.add(DemandSignal.SELL_OUT_SPEED);
    }
    return available;
  }

  private static boolean discounted(VariantData v) {
    return v.compareAtPrice() != null && v.compareAtPrice().compareTo(v.price()) > 0;
  }
}

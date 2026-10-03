package com.beacon.insight;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.CollectionData;
import com.beacon.adapter.model.ProductData;
import com.beacon.config.Assumptions;
import com.beacon.size.SizeAnalyzer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A snapshot prepared for the insight rules, plus what changed since the previous snapshot. Pure:
 * no network or database access.
 *
 * @param soldOutSinceLastCheck external ids of variants that sold out in the latest snapshot window
 * @param hasPreviousSnapshot false on a store's first snapshot (no change data yet)
 */
public record StoreView(
    CatalogSnapshotData data,
    List<ProductView> products,
    Set<Long> bestSellerProductIds,
    Set<Long> promotedProductIds,
    Set<Long> soldOutSinceLastCheck,
    boolean hasPreviousSnapshot) {

  public StoreView {
    products = List.copyOf(products);
    bestSellerProductIds = Set.copyOf(bestSellerProductIds);
    promotedProductIds = Set.copyOf(promotedProductIds);
    soldOutSinceLastCheck = Set.copyOf(soldOutSinceLastCheck);
  }

  public boolean hasBestSellerCollection() {
    return !data.collections().bestSeller().isEmpty();
  }

  public static StoreView build(
      CatalogSnapshotData data,
      Assumptions assumptions,
      Set<Long> soldOutSinceLastCheck,
      boolean hasPreviousSnapshot) {
    Set<Long> bestSellers = new HashSet<>();
    data.collections().bestSeller().forEach(c -> bestSellers.addAll(c.productExternalIds()));
    Set<Long> promoted = new HashSet<>(bestSellers);
    for (CollectionData featured : data.collections().featured()) {
      promoted.addAll(featured.productExternalIds());
    }
    Set<String> homeHandles =
        data.signals() == null ? Set.of() : new HashSet<>(data.signals().home().productHandles());
    for (ProductData p : data.products()) {
      if (homeHandles.contains(p.handle())) {
        promoted.add(p.externalId());
      }
    }
    ExclusionRules exclusions = new ExclusionRules(assumptions.exclusions());
    SizeAnalyzer sizes = new SizeAnalyzer(assumptions.coreSizes().percent());
    String base = "https://" + data.store().domain() + "/products/";
    List<ProductView> views =
        data.products().stream()
            .map(
                p ->
                    new ProductView(
                        p,
                        sizes.analyse(p),
                        exclusions.exclusion(p, bestSellers),
                        bestSellers.contains(p.externalId()),
                        promoted.contains(p.externalId()),
                        base + p.handle()))
            .toList();
    return new StoreView(
        data, views, bestSellers, promoted, soldOutSinceLastCheck, hasPreviousSnapshot);
  }
}

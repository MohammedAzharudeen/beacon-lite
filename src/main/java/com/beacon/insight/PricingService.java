package com.beacon.insight;

import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.ShippingSignals;
import com.beacon.adapter.model.VariantData;
import com.beacon.config.Assumptions;
import com.beacon.insight.report.PricingInsights;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pricing findings. */
public final class PricingService {

  private static final List<int[]> BUCKETS =
      List.of(new int[] {1, 10}, new int[] {10, 30}, new int[] {30, 50}, new int[] {50, 101});

  private final Assumptions.Pricing config;

  public PricingService(Assumptions.Pricing config) {
    this.config = config;
  }

  public PricingInsights assess(StoreView store) {
    int equalsPrice = 0;
    int discounted = 0;
    int deep = 0;
    int deadStock = 0;
    Map<String, Integer> buckets = new LinkedHashMap<>();
    BUCKETS.forEach(b -> buckets.put(label(b), 0));
    List<BigDecimal> prices = new ArrayList<>();
    for (ProductView view : store.products()) {
      ProductData p = view.product();
      if (p.variants().isEmpty()) {
        continue;
      }
      prices.add(view.price());
      if (p.variants().stream()
          .anyMatch(
              v -> v.compareAtPrice() != null && v.compareAtPrice().compareTo(v.price()) == 0)) {
        equalsPrice++;
      }
      int depth = maxDiscountPercent(p);
      if (depth > 0) {
        discounted++;
        for (int[] b : BUCKETS) {
          if (depth >= b[0] && depth < b[1]) {
            buckets.merge(label(b), 1, Integer::sum);
          }
        }
        if (depth >= config.deepDiscountPercent()) {
          deep++;
        }
      }
      boolean inStock = p.variants().stream().anyMatch(VariantData::available);
      boolean old =
          p.publishedAt() != null
              && Duration.between(p.publishedAt(), store.data().capturedAt()).toDays()
                  > config.deadStock().publishedOlderThanDays();
      if (inStock && old && depth >= config.deadStock().discountAtLeastPercent()) {
        deadStock++;
      }
    }
    return new PricingInsights(
        equalsPrice,
        discounted,
        buckets,
        deep,
        config.deepDiscountPercent(),
        deadStock,
        freeShipping(store, prices));
  }

  /** Largest discount across a product's variants, in whole percent. */
  static int maxDiscountPercent(ProductData p) {
    int max = 0;
    for (VariantData v : p.variants()) {
      if (v.compareAtPrice() != null
          && v.compareAtPrice().signum() > 0
          && v.compareAtPrice().compareTo(v.price()) > 0) {
        BigDecimal off =
            v.compareAtPrice()
                .subtract(v.price())
                .multiply(BigDecimal.valueOf(100))
                .divide(v.compareAtPrice(), 0, RoundingMode.HALF_UP);
        max = Math.max(max, off.intValue());
      }
    }
    return max;
  }

  private static PricingInsights.FreeShipping freeShipping(
      StoreView store, List<BigDecimal> prices) {
    BigDecimal median = median(prices);
    ShippingSignals shipping =
        store.data().signals() == null ? null : store.data().signals().shipping();
    if (shipping == null || shipping.text() == null) {
      return new PricingInsights.FreeShipping(null, null, median, null, null);
    }
    Double ratio =
        shipping.threshold() == null || median == null || median.signum() == 0
            ? null
            : shipping.threshold().divide(median, 2, RoundingMode.HALF_UP).doubleValue();
    return new PricingInsights.FreeShipping(
        shipping.text(), shipping.threshold(), median, ratio, shipping.source());
  }

  static BigDecimal median(List<BigDecimal> values) {
    if (values.isEmpty()) {
      return null;
    }
    List<BigDecimal> sorted = values.stream().sorted().toList();
    int n = sorted.size();
    return n % 2 == 1
        ? sorted.get(n / 2)
        : sorted
            .get(n / 2 - 1)
            .add(sorted.get(n / 2))
            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
  }

  private static String label(int[] bucket) {
    return bucket[1] > 100 ? bucket[0] + "%+" : bucket[0] + "–" + bucket[1] + "%";
  }
}

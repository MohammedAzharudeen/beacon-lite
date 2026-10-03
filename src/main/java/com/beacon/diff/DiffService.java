package com.beacon.diff;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.catalog.Product;
import com.beacon.catalog.Variant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares the latest known catalog state with a new snapshot. Products and variants are matched by
 * the store's ids, never by title, so a renamed product produces no add/remove pair. Timing comes
 * only from snapshot windows.
 */
public final class DiffService {

  private DiffService() {}

  /**
   * A change found before it is saved (store ids, not database ids).
   *
   * @param variantExternalId {@code null} for product-level changes
   */
  public record PendingChange(
      ChangeType type,
      long productExternalId,
      Long variantExternalId,
      String oldValue,
      String newValue) {}

  /**
   * @param products latest known products of the store, by external id
   * @param variants latest known variants of the store, by external id
   */
  public static List<PendingChange> diff(
      Map<Long, Product> products, Map<Long, Variant> variants, CatalogSnapshotData data) {
    List<PendingChange> changes = new ArrayList<>();
    Set<Long> seenProducts = new HashSet<>();
    Set<Long> seenVariants = new HashSet<>();
    for (ProductData p : data.products()) {
      seenProducts.add(p.externalId());
      Product known = products.get(p.externalId());
      if (known == null || known.isRemoved()) {
        changes.add(
            new PendingChange(ChangeType.PRODUCT_ADDED, p.externalId(), null, null, p.title()));
      }
      for (VariantData v : p.variants()) {
        seenVariants.add(v.externalId());
        Variant old = variants.get(v.externalId());
        if (old == null || old.isRemoved()) {
          if (known != null && !known.isRemoved()) {
            changes.add(
                new PendingChange(
                    ChangeType.VARIANT_ADDED, p.externalId(), v.externalId(), null, v.title()));
          }
          continue;
        }
        if (old.isAvailable() && !v.available()) {
          changes.add(
              new PendingChange(
                  ChangeType.SOLD_OUT, p.externalId(), v.externalId(), "available", "sold out"));
        } else if (!old.isAvailable() && v.available()) {
          changes.add(
              new PendingChange(
                  ChangeType.RESTOCKED, p.externalId(), v.externalId(), "sold out", "available"));
        }
        if (!same(old.getPrice(), v.price())
            || !same(old.getCompareAtPrice(), v.compareAtPrice())) {
          changes.add(
              new PendingChange(
                  ChangeType.PRICE_CHANGED,
                  p.externalId(),
                  v.externalId(),
                  price(old.getPrice(), old.getCompareAtPrice()),
                  price(v.price(), v.compareAtPrice())));
        }
      }
    }
    for (Product known : products.values()) {
      if (!known.isRemoved() && !seenProducts.contains(known.getExternalId())) {
        changes.add(
            new PendingChange(
                ChangeType.PRODUCT_REMOVED, known.getExternalId(), null, known.getTitle(), null));
      }
    }
    Map<Long, Long> productExternalByDbId = new HashMap<>();
    products.values().forEach(p -> productExternalByDbId.put(p.getId(), p.getExternalId()));
    for (Variant known : variants.values()) {
      Long productExternal = productExternalByDbId.get(known.getProductId());
      if (!known.isRemoved()
          && !seenVariants.contains(known.getExternalId())
          && productExternal != null
          && seenProducts.contains(productExternal)) {
        changes.add(
            new PendingChange(
                ChangeType.VARIANT_REMOVED,
                productExternal,
                known.getExternalId(),
                known.getTitle(),
                null));
      }
    }
    return changes;
  }

  private static boolean same(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return Objects.equals(a, b);
    }
    return a.compareTo(b) == 0;
  }

  private static String price(BigDecimal price, BigDecimal compareAt) {
    String p = price == null ? "?" : price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    return compareAt == null
        ? p
        : p + " (was " + compareAt.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
  }
}

package com.beacon.catalog;

import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.diff.ChangeEvent;
import com.beacon.diff.ChangeEventRepository;
import com.beacon.diff.DiffService;
import com.beacon.diff.SnapshotWindow;
import com.beacon.insight.ProductView;
import com.beacon.insight.StoreView;
import com.beacon.size.SizeAnalyzer;
import com.beacon.size.SizeEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes a snapshot's catalog as the latest known state and records what changed. Called inside the
 * snapshot's transaction.
 */
@Service
public class CatalogUpsertService {

  private static final Logger log = LoggerFactory.getLogger(CatalogUpsertService.class);
  private static final int TITLE_MAX = 500;
  private static final int TEXT_MAX = 255;
  private static final int URL_MAX = 1000;

  private final ProductRepository products;
  private final VariantRepository variants;
  private final ChangeEventRepository changes;

  public CatalogUpsertService(
      ProductRepository products, VariantRepository variants, ChangeEventRepository changes) {
    this.products = products;
    this.variants = variants;
    this.changes = changes;
  }

  /**
   * @param window the previous → current snapshot window, or {@code null} on the first snapshot (no
   *     change events then)
   * @return number of change events written
   */
  public int upsert(long storeId, long snapshotId, StoreView view, SnapshotWindow window) {
    Map<Long, Product> knownProducts = new HashMap<>();
    products.findByStoreId(storeId).forEach(p -> knownProducts.put(p.getExternalId(), p));
    Map<Long, Variant> knownVariants = new HashMap<>();
    variants.findByStoreId(storeId).forEach(v -> knownVariants.put(v.getExternalId(), v));
    List<DiffService.PendingChange> pending =
        window == null ? List.of() : DiffService.diff(knownProducts, knownVariants, view.data());

    Set<Long> seenProducts = new HashSet<>();
    Set<Long> seenVariants = new HashSet<>();
    List<Product> productRows = new ArrayList<>();
    for (ProductView pv : view.products()) {
      ProductData p = pv.product();
      seenProducts.add(p.externalId());
      Product row =
          knownProducts.computeIfAbsent(p.externalId(), id -> Product.create(storeId, id));
      row.apply(fields(pv), snapshotId);
      productRows.add(row);
    }
    products.saveAll(productRows);
    for (Product known : knownProducts.values()) {
      if (!seenProducts.contains(known.getExternalId()) && !known.isRemoved()) {
        known.markRemoved(snapshotId);
      }
    }

    List<Variant> variantRows = new ArrayList<>();
    for (ProductView pv : view.products()) {
      Product parent = knownProducts.get(pv.product().externalId());
      Map<String, SizeEntry> sizes = pv.sizes().byLabel();
      for (VariantData v : pv.product().variants()) {
        seenVariants.add(v.externalId());
        Variant row =
            knownVariants.computeIfAbsent(
                v.externalId(), id -> Variant.create(storeId, parent.getId(), id));
        String label = SizeAnalyzer.labelOf(pv.product(), v);
        SizeEntry size = label == null ? null : sizes.get(label);
        row.apply(
            new VariantFields(
                cut(v.title(), TITLE_MAX, "Default"),
                cut(v.option1(), TEXT_MAX, null),
                cut(v.option2(), TEXT_MAX, null),
                cut(v.option3(), TEXT_MAX, null),
                size == null ? null : cut(label, 100, null),
                size == null ? null : size.kind(),
                size == null ? null : size.rank(),
                size != null && size.core(),
                cut(v.sku(), TEXT_MAX, null),
                v.price(),
                v.compareAtPrice(),
                v.available()),
            snapshotId);
        variantRows.add(row);
      }
    }
    variants.saveAll(variantRows);
    for (Variant known : knownVariants.values()) {
      if (!seenVariants.contains(known.getExternalId()) && !known.isRemoved()) {
        known.markRemoved(snapshotId);
      }
    }

    List<ChangeEvent> events = new ArrayList<>();
    for (DiffService.PendingChange c : pending) {
      Product product = knownProducts.get(c.productExternalId());
      Variant variant =
          c.variantExternalId() == null ? null : knownVariants.get(c.variantExternalId());
      events.add(
          ChangeEvent.of(
              storeId,
              window,
              c.type(),
              product == null ? null : product.getId(),
              variant == null ? null : variant.getId(),
              cut(c.oldValue(), TEXT_MAX, null),
              cut(c.newValue(), TEXT_MAX, null)));
    }
    changes.saveAll(events);
    log.info(
        "[UPSERT] store={} snapshot={} products={} variants={} changes={}",
        storeId,
        snapshotId,
        productRows.size(),
        variantRows.size(),
        events.size());
    return events.size();
  }

  private static ProductFields fields(ProductView pv) {
    ProductData p = pv.product();
    return new ProductFields(
        cut(p.handle(), TEXT_MAX, ""),
        cut(p.title(), TITLE_MAX, "(untitled)"),
        cut(p.productType(), TEXT_MAX, null),
        cut(p.vendor(), TEXT_MAX, null),
        p.tags().isEmpty() ? null : String.join("|", p.tags()),
        p.imageUrls().isEmpty() ? null : cut(p.imageUrls().get(0), URL_MAX, null),
        p.imageUrls().size(),
        p.descriptionLength(),
        p.publishedAt(),
        pv.inBestSellerCollection(),
        pv.promoted(),
        pv.exclusion().orElse(null));
  }

  private static String cut(String value, int max, String fallback) {
    if (value == null || value.isEmpty()) {
      return fallback;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}

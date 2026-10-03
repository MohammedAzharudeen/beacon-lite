package com.beacon.testsupport;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Derives a "next check" from a real recorded snapshot by applying small, explicit edits, so change
 * detection can be tested on real catalog data.
 */
public final class SnapshotEdits {

  private SnapshotEdits() {}

  public static CatalogSnapshotData later(
      CatalogSnapshotData data, Duration after, UnaryOperator<List<ProductData>> edit) {
    List<ProductData> products = edit.apply(new ArrayList<>(data.products()));
    return new CatalogSnapshotData(
        data.schemaVersion(),
        data.store(),
        data.capturedAt().plus(after),
        data.robots(),
        data.catalog(),
        products,
        data.collections(),
        data.signals());
  }

  public static ProductData renamed(ProductData p, String title) {
    return new ProductData(
        p.externalId(),
        p.handle(),
        title,
        p.productType(),
        p.vendor(),
        p.tags(),
        p.imageUrls(),
        p.descriptionLength(),
        p.publishedAt(),
        p.options(),
        p.variants());
  }

  /** Marks the first available variant as sold out. */
  public static ProductData sellOutFirstAvailable(ProductData p) {
    List<VariantData> variants = new ArrayList<>();
    boolean done = false;
    for (VariantData v : p.variants()) {
      if (!done && v.available()) {
        variants.add(
            new VariantData(
                v.externalId(),
                v.title(),
                v.option1(),
                v.option2(),
                v.option3(),
                v.sku(),
                false,
                v.price(),
                v.compareAtPrice()));
        done = true;
      } else {
        variants.add(v);
      }
    }
    return new ProductData(
        p.externalId(),
        p.handle(),
        p.title(),
        p.productType(),
        p.vendor(),
        p.tags(),
        p.imageUrls(),
        p.descriptionLength(),
        p.publishedAt(),
        p.options(),
        variants);
  }
}

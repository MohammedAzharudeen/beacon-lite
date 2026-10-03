package com.beacon.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.catalog.Product;
import com.beacon.catalog.ProductFields;
import com.beacon.catalog.Variant;
import com.beacon.catalog.VariantFields;
import com.beacon.testsupport.DemoSnapshots;
import com.beacon.testsupport.SnapshotEdits;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiffServiceTest {

  private final CatalogSnapshotData base = DemoSnapshots.reebok();

  @Test
  void diff_renamedProduct_noAddOrRemove() {
    ProductData first = base.products().get(0);
    CatalogSnapshotData next =
        SnapshotEdits.later(
            base,
            Duration.ofHours(6),
            list -> {
              list.set(0, SnapshotEdits.renamed(first, first.title() + " (renamed)"));
              return list;
            });

    List<DiffService.PendingChange> changes =
        DiffService.diff(products(base), variants(base), next);

    assertThat(changes).isEmpty();
  }

  @Test
  void diff_soldOutRemovedAndAdded_detected() throws Exception {
    int target = indexWithAvailableVariant(base);
    ProductData removed = base.products().get(base.products().size() - 1);
    CatalogSnapshotData next =
        SnapshotEdits.later(
            base,
            Duration.ofHours(6),
            list -> {
              list.set(target, SnapshotEdits.sellOutFirstAvailable(list.get(target)));
              list.remove(list.size() - 1);
              return list;
            });
    Map<Long, Product> known = products(base);
    known.remove(base.products().get(1).externalId()); // pretend product 1 is new this time

    List<DiffService.PendingChange> changes = DiffService.diff(known, variants(base), next);

    assertThat(changes)
        .extracting(DiffService.PendingChange::type)
        .contains(ChangeType.SOLD_OUT, ChangeType.PRODUCT_REMOVED, ChangeType.PRODUCT_ADDED);
    assertThat(changes)
        .filteredOn(c -> c.type() == ChangeType.PRODUCT_REMOVED)
        .singleElement()
        .extracting(DiffService.PendingChange::productExternalId)
        .isEqualTo(removed.externalId());
  }

  @Test
  void diff_priceChange_recordsOldAndNew() {
    CatalogSnapshotData next =
        SnapshotEdits.later(
            base,
            Duration.ofHours(6),
            list -> {
              ProductData p = list.get(0);
              VariantData v = p.variants().get(0);
              List<VariantData> vs = new java.util.ArrayList<>(p.variants());
              vs.set(
                  0,
                  new VariantData(
                      v.externalId(),
                      v.title(),
                      v.option1(),
                      v.option2(),
                      v.option3(),
                      v.sku(),
                      v.available(),
                      v.price().add(java.math.BigDecimal.TEN),
                      v.compareAtPrice()));
              list.set(
                  0,
                  new ProductData(
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
                      vs));
              return list;
            });

    List<DiffService.PendingChange> changes =
        DiffService.diff(products(base), variants(base), next);

    assertThat(changes)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.type()).isEqualTo(ChangeType.PRICE_CHANGED);
              assertThat(c.oldValue()).isNotEqualTo(c.newValue());
            });
  }

  private static int indexWithAvailableVariant(CatalogSnapshotData data) {
    for (int i = 0; i < data.products().size() - 1; i++) {
      if (data.products().get(i).variants().stream().anyMatch(VariantData::available)) {
        return i;
      }
    }
    throw new IllegalStateException("no available variant");
  }

  private static Map<Long, Product> products(CatalogSnapshotData data) {
    Map<Long, Product> map = new HashMap<>();
    long id = 1;
    for (ProductData p : data.products()) {
      Product row = Product.create(1L, p.externalId());
      row.apply(
          new ProductFields(
              p.handle(),
              p.title(),
              p.productType(),
              null,
              null,
              null,
              0,
              0,
              null,
              false,
              false,
              null),
          1L);
      setId(row, id++);
      map.put(p.externalId(), row);
    }
    return map;
  }

  private static Map<Long, Variant> variants(CatalogSnapshotData data) {
    Map<Long, Variant> map = new HashMap<>();
    long productId = 1;
    for (ProductData p : data.products()) {
      for (VariantData v : p.variants()) {
        Variant row = Variant.create(1L, productId, v.externalId());
        row.apply(
            new VariantFields(
                v.title(),
                v.option1(),
                v.option2(),
                v.option3(),
                null,
                null,
                null,
                false,
                v.sku(),
                v.price(),
                v.compareAtPrice(),
                v.available()),
            1L);
        map.put(v.externalId(), row);
      }
      productId++;
    }
    return map;
  }

  private static void setId(Object entity, long id) {
    try {
      Field f = entity.getClass().getDeclaredField("id");
      f.setAccessible(true);
      f.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}

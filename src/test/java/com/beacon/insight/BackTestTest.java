package com.beacon.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.config.AssumptionsLoader;
import com.beacon.testsupport.DemoSnapshots;
import com.beacon.testsupport.SnapshotEdits;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Back-test mechanics on real catalog data with explicit, small edits between "checks". */
class BackTestTest {

  private final BackTest backTest = new BackTest(AssumptionsLoader.packaged());

  @Test
  void flagThenMoreSizesSellOut_countsAsHit() {
    CatalogSnapshotData base = DemoSnapshots.steveMadden();
    long target = productWithCoreAvailable(base);
    CatalogSnapshotData second = sellOut(base, target, 6, 2); // core size sells out → selling fast
    CatalogSnapshotData third =
        sellOut(second, target, 12, 1); // another size sells out within 24 h

    BackTest.Result result = backTest.run(List.of(base, second, third));

    assertThat(result.flags()).isGreaterThanOrEqualTo(1);
    assertThat(result.evaluated()).isGreaterThanOrEqualTo(1);
    assertThat(result.hits()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void singleSnapshot_nothingToEvaluate() {
    BackTest.Result result = backTest.run(List.of(DemoSnapshots.reebok()));

    assertThat(result.flags()).isZero();
    assertThat(result.hitRate()).isNull();
  }

  private static long productWithCoreAvailable(CatalogSnapshotData data) {
    for (ProductData p : data.products()) {
      long available = p.variants().stream().filter(VariantData::available).count();
      if (available >= 6
          && p.variants().size() >= 10
          && p.tags().stream().noneMatch(t -> t.toLowerCase().contains("order"))) {
        return p.externalId();
      }
    }
    throw new IllegalStateException("no suitable product");
  }

  /** Sells out {@code count} available variants of one product, {@code hours} after the input. */
  private static CatalogSnapshotData sellOut(
      CatalogSnapshotData data, long productId, int hours, int count) {
    return SnapshotEdits.later(
        data,
        Duration.ofHours(hours),
        list -> {
          for (int i = 0; i < list.size(); i++) {
            if (list.get(i).externalId() == productId) {
              ProductData p = list.get(i);
              for (int k = 0; k < count; k++) {
                p = SnapshotEdits.sellOutFirstAvailable(middleFirst(p));
              }
              list.set(i, p);
            }
          }
          return list;
        });
  }

  /** Puts the middle variant first so the edit hits a core size. */
  private static ProductData middleFirst(ProductData p) {
    List<VariantData> vs = new java.util.ArrayList<>(p.variants());
    int mid = vs.size() / 2;
    for (int j = mid; j < vs.size(); j++) {
      if (vs.get(j).available()) {
        VariantData v = vs.remove(j);
        vs.add(0, v);
        break;
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
        vs);
  }
}

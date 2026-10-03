package com.beacon.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.config.Assumptions;
import com.beacon.config.AssumptionsLoader;
import com.beacon.insight.report.CatalogQuality;
import com.beacon.testsupport.DemoSnapshots;
import com.beacon.testsupport.SnapshotEdits;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogQualityServiceTest {

  private final Assumptions assumptions = AssumptionsLoader.packaged();

  @Test
  void assess_spreadsheetErrorAndCaseVariantTypes_flagged() {
    CatalogSnapshotData edited =
        SnapshotEdits.later(
            DemoSnapshots.reebok(),
            Duration.ZERO,
            list -> {
              list.set(0, withType(list.get(0), "#REF!"));
              list.set(1, withType(list.get(1), list.get(2).productType().toUpperCase()));
              return list;
            });

    CatalogQuality quality =
        new CatalogQualityService(assumptions.catalog())
            .assess(StoreView.build(edited, assumptions, Set.of(), false));

    assertThat(quality.spreadsheetErrors()).contains("#REF!");
    assertThat(quality.caseVariantTypes()).isNotEmpty();
  }

  private static ProductData withType(ProductData p, String type) {
    return new ProductData(
        p.externalId(),
        p.handle(),
        p.title(),
        type,
        p.vendor(),
        p.tags(),
        p.imageUrls(),
        p.descriptionLength(),
        p.publishedAt(),
        p.options(),
        p.variants());
  }
}

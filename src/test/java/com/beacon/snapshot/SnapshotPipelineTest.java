package com.beacon.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.Platform;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.catalog.ProductRepository;
import com.beacon.diff.ChangeEvent;
import com.beacon.diff.ChangeEventRepository;
import com.beacon.diff.ChangeType;
import com.beacon.insight.ReportService;
import com.beacon.insight.report.InsightReport;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.store.StoreStatus;
import com.beacon.testsupport.DemoSnapshots;
import com.beacon.testsupport.SnapshotEdits;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** Two real-data snapshots through the whole pipeline: raw file, catalog, changes, report. */
@SpringBootTest
@ActiveProfiles("test")
class SnapshotPipelineTest {

  @Autowired SnapshotService snapshots;
  @Autowired StoreRepository stores;
  @Autowired ProductRepository products;
  @Autowired ChangeEventRepository changes;
  @Autowired SnapshotRepository snapshotRows;
  @Autowired ReportService reports;

  @Test
  void ingestTwoSnapshots_firstHasNoChanges_secondRecordsWindowedChanges() {
    CatalogSnapshotData first = DemoSnapshots.reebok();
    Store store = stores.save(Store.adding("pipeline-test.reebok.com", "Reebok", Platform.SHOPIFY));

    long firstId = snapshots.ingestRecorded(store.getId(), first);

    InsightReport firstReport = reports.require(store.getId());
    assertThat(firstReport.snapshotId()).isEqualTo(firstId);
    assertThat(firstReport.kpis().changedSinceLastCheck()).isNull();
    assertThat(changes.findByStoreIdAndToSnapshotId(store.getId(), firstId)).isEmpty();
    assertThat(stores.findById(store.getId()).orElseThrow().getStatus())
        .isEqualTo(StoreStatus.ACTIVE);
    assertThat(stores.findById(store.getId()).orElseThrow().getDisplayName()).isEqualTo("Reebok");

    int target = 0;
    while (first.products().get(target).variants().stream().noneMatch(VariantData::available)) {
      target++;
    }
    int soldOutIndex = target;
    ProductData removed = first.products().get(first.products().size() - 1);
    CatalogSnapshotData second =
        SnapshotEdits.later(
            first,
            Duration.ofHours(6),
            list -> {
              list.set(soldOutIndex, SnapshotEdits.sellOutFirstAvailable(list.get(soldOutIndex)));
              list.set(1, SnapshotEdits.renamed(list.get(1), list.get(1).title() + " v2"));
              list.remove(list.size() - 1);
              return list;
            });

    long secondId = snapshots.ingestRecorded(store.getId(), second);

    List<ChangeEvent> events = changes.findByStoreIdAndToSnapshotId(store.getId(), secondId);
    assertThat(events)
        .extracting(ChangeEvent::getType)
        .containsExactlyInAnyOrder(ChangeType.SOLD_OUT, ChangeType.PRODUCT_REMOVED);
    assertThat(events)
        .allSatisfy(
            e -> {
              assertThat(e.getWindowStart()).isEqualTo(first.capturedAt());
              assertThat(e.getWindowEnd()).isEqualTo(second.capturedAt());
              assertThat(e.getFromSnapshotId()).isEqualTo(firstId);
            });
    assertThat(
            products
                .findByStoreIdAndExternalId(store.getId(), removed.externalId())
                .orElseThrow()
                .isRemoved())
        .isTrue();
    InsightReport secondReport = reports.require(store.getId());
    assertThat(secondReport.snapshotId()).isEqualTo(secondId);
    assertThat(secondReport.kpis().changedSinceLastCheck().soldOut()).isEqualTo(1);
    assertThat(snapshotRows.findById(secondId).orElseThrow().getRawPath()).isNotNull();
    assertThat(changes.findByStoreIdOrderByWindowEndDesc(store.getId(), PageRequest.of(0, 10)))
        .hasSize(2);
  }
}

package com.beacon.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.action.ActionState;
import com.beacon.action.ActionStatus;
import com.beacon.action.ActionStatusRepository;
import com.beacon.adapter.Platform;
import com.beacon.diff.ChangeEvent;
import com.beacon.diff.ChangeEventRepository;
import com.beacon.diff.ChangeType;
import com.beacon.diff.SnapshotWindow;
import com.beacon.insight.InsightReportEntity;
import com.beacon.insight.InsightReportRepository;
import com.beacon.job.Job;
import com.beacon.job.JobRepository;
import com.beacon.job.JobStatus;
import com.beacon.job.JobType;
import com.beacon.size.SizeKind;
import com.beacon.snapshot.Snapshot;
import com.beacon.snapshot.SnapshotRepository;
import com.beacon.snapshot.SnapshotStatus;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the Liquibase schema with Hibernate validation and checks every repository and constraint.
 */
@DataJpaTest
@ActiveProfiles("test")
class PersistenceTest {

  @Autowired private StoreRepository stores;
  @Autowired private SnapshotRepository snapshots;
  @Autowired private ProductRepository products;
  @Autowired private VariantRepository variants;
  @Autowired private ChangeEventRepository changes;
  @Autowired private JobRepository jobs;
  @Autowired private ActionStatusRepository actions;
  @Autowired private InsightReportRepository reports;

  private Store store;
  private Snapshot snapshot;

  @BeforeEach
  void setUp() {
    store = stores.save(Store.adding("stevemadden.com", "Steve Madden", Platform.SHOPIFY));
    snapshot = snapshots.save(Snapshot.running(store.getId(), 1, Instant.now()));
  }

  @Test
  void store_findByDomain_returnsSavedStore() {
    assertThat(stores.findByDomain("stevemadden.com")).isPresent();
  }

  @Test
  void store_activate_setsCurrentSnapshotAndCurrency() {
    snapshot.complete("data/snapshots/1/x.json.gz", 2513, 24122, Instant.now());
    store.activate(snapshot.getId(), "USD");
    stores.flush();

    Store reloaded = stores.findById(store.getId()).orElseThrow();
    assertThat(reloaded.getCurrentSnapshotId()).isEqualTo(snapshot.getId());
    assertThat(reloaded.getCurrency()).isEqualTo("USD");
    assertThat(
            snapshots.findTopByStoreIdAndStatusOrderByStartedAtDesc(
                store.getId(), SnapshotStatus.COMPLETE))
        .isPresent();
  }

  @Test
  void product_and_variant_roundTrip() {
    Product product = Product.create(store.getId(), 7307477418117L);
    product.apply(
        new ProductFields(
            "geronimo-black-patent",
            "GERONIMO BLACK PATENT",
            "Men's Shoes",
            "Steve Madden",
            "Back-Order|Black",
            "https://cdn.shopify.com/x.jpg",
            5,
            578,
            Instant.now(),
            false,
            false,
            null),
        snapshot.getId());
    products.saveAndFlush(product);

    Variant variant = Variant.create(store.getId(), product.getId(), 41771062853765L);
    variant.apply(
        new VariantFields(
            "BLACK PATENT / 7 / 018",
            "BLACK PATENT",
            "7",
            "018",
            "7",
            SizeKind.NUMERIC,
            0,
            false,
            "GERONIMO",
            new BigDecimal("179.95"),
            new BigDecimal("179.95"),
            true),
        snapshot.getId());
    variants.saveAndFlush(variant);

    assertThat(products.findByStoreIdAndExternalId(store.getId(), 7307477418117L)).isPresent();
    assertThat(variants.findByProductIdIn(List.of(product.getId())))
        .singleElement()
        .satisfies(v -> assertThat(v.getPrice()).isEqualByComparingTo("179.95"));
  }

  @Test
  void product_duplicateExternalId_isRejected() {
    products.saveAndFlush(withMinimalFields(Product.create(store.getId(), 1L)));

    assertThatThrownBy(
            () -> products.saveAndFlush(withMinimalFields(Product.create(store.getId(), 1L))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void changeEvent_isStoredWithWindow() {
    Snapshot next = snapshots.save(Snapshot.running(store.getId(), 1, Instant.now()));
    SnapshotWindow window =
        new SnapshotWindow(
            snapshot.getId(), next.getId(), Instant.now().minusSeconds(21600), Instant.now());
    changes.saveAndFlush(
        ChangeEvent.of(store.getId(), window, ChangeType.SOLD_OUT, null, null, "true", "false"));

    assertThat(changes.findByStoreIdOrderByWindowEndDesc(store.getId(), PageRequest.of(0, 10)))
        .singleElement()
        .satisfies(e -> assertThat(e.getType()).isEqualTo(ChangeType.SOLD_OUT));
  }

  @Test
  void job_activeLookup_findsQueuedJob() {
    jobs.saveAndFlush(Job.queued(store.getId(), JobType.ADD_STORE));

    assertThat(
            jobs.findFirstByStoreIdAndStatusIn(
                store.getId(), List.of(JobStatus.QUEUED, JobStatus.RUNNING)))
        .isPresent();
  }

  @Test
  void actionStatus_duplicateKey_isRejected() {
    actions.saveAndFlush(ActionStatus.of(store.getId(), "RESTOCK:product:1", ActionState.DONE));

    assertThatThrownBy(
            () ->
                actions.saveAndFlush(
                    ActionStatus.of(store.getId(), "RESTOCK:product:1", ActionState.TODO)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insightReport_latestIsReturned() {
    reports.saveAndFlush(
        InsightReportEntity.of(store.getId(), snapshot.getId(), "abc123", Instant.now(), "{}"));

    assertThat(reports.findTopByStoreIdOrderByGeneratedAtDesc(store.getId()))
        .get()
        .satisfies(r -> assertThat(r.getAssumptionsVersion()).isEqualTo("abc123"));
  }

  private Product withMinimalFields(Product product) {
    product.apply(
        new ProductFields("h", "t", null, null, null, null, 0, 0, null, false, false, null),
        snapshot.getId());
    return product;
  }
}

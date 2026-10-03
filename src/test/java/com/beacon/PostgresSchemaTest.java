package com.beacon;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.Platform;
import com.beacon.insight.ReportService;
import com.beacon.snapshot.SnapshotService;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.testsupport.DemoSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same Liquibase changelog and JPA mapping on PostgreSQL (runs in CI with a Postgres service;
 * skipped locally unless BEACON_TEST_PG_URL is set).
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "BEACON_TEST_PG_URL", matches = ".+")
class PostgresSchemaTest {

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("BEACON_TEST_PG_URL"));
    registry.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("BEACON_TEST_PG_USER", "beacon"));
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("BEACON_TEST_PG_PASSWORD", "beacon"));
  }

  @Autowired StoreRepository stores;
  @Autowired SnapshotService snapshots;
  @Autowired ReportService reports;

  @Test
  void schemaValidates_andARealSnapshotIngests() {
    // A new domain each run, so the test also passes against a database it has used before
    String domain = "pg-test-" + System.currentTimeMillis() + ".reebok.com";
    Store store = stores.save(Store.adding(domain, "Reebok", Platform.SHOPIFY));

    snapshots.ingestRecorded(store.getId(), DemoSnapshots.reebok());

    assertThat(reports.require(store.getId()).catalog().products()).isEqualTo(1361);
  }
}

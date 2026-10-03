package com.beacon.insight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A stored insight report (DEVSPEC 4.9). Keeps the snapshot id and assumptions version so every
 * number shown can be traced and recomputed.
 */
@Entity
@Table(name = "insight_report")
public class InsightReportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "snapshot_id", nullable = false, unique = true)
  private Long snapshotId;

  @Column(name = "assumptions_version", nullable = false)
  private String assumptionsVersion;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt;

  @Lob
  @Column(nullable = false)
  private String payload;

  protected InsightReportEntity() {}

  public static InsightReportEntity of(
      Long storeId,
      Long snapshotId,
      String assumptionsVersion,
      Instant generatedAt,
      String payload) {
    InsightReportEntity report = new InsightReportEntity();
    report.storeId = storeId;
    report.snapshotId = snapshotId;
    report.assumptionsVersion = assumptionsVersion;
    report.generatedAt = generatedAt;
    report.payload = payload;
    return report;
  }

  public Long getId() {
    return id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public Long getSnapshotId() {
    return snapshotId;
  }

  public String getAssumptionsVersion() {
    return assumptionsVersion;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public String getPayload() {
    return payload;
  }
}

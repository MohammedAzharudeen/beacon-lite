package com.beacon.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One snapshot run of a store (DEVSPEC 4.3). */
@Entity
@Table(name = "snapshot")
public class Snapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SnapshotStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "product_count")
  private Integer productCount;

  @Column(name = "variant_count")
  private Integer variantCount;

  @Column(name = "raw_path")
  private String rawPath;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  protected Snapshot() {}

  public static Snapshot running(Long storeId, int schemaVersion, Instant startedAt) {
    Snapshot snapshot = new Snapshot();
    snapshot.storeId = storeId;
    snapshot.schemaVersion = schemaVersion;
    snapshot.status = SnapshotStatus.RUNNING;
    snapshot.startedAt = startedAt;
    return snapshot;
  }

  public void complete(String rawPath, int productCount, int variantCount, Instant finishedAt) {
    this.status = SnapshotStatus.COMPLETE;
    this.rawPath = rawPath;
    this.productCount = productCount;
    this.variantCount = variantCount;
    this.finishedAt = finishedAt;
  }

  public void fail(String errorCode, String errorMessage, Instant finishedAt) {
    this.status = SnapshotStatus.FAILED;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.finishedAt = finishedAt;
  }

  public Long getId() {
    return id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public SnapshotStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Integer getProductCount() {
    return productCount;
  }

  public Integer getVariantCount() {
    return variantCount;
  }

  public String getRawPath() {
    return rawPath;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}

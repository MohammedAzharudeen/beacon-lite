package com.beacon.job;

import com.beacon.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A background snapshot job with progress shown in the UI. */
@Entity
@Table(name = "job")
public class Job {

  @Id private UUID id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus status;

  @Enumerated(EnumType.STRING)
  private JobStep step;

  @Column(name = "products_read", nullable = false)
  private int productsRead;

  @Column(name = "products_estimate")
  private Integer productsEstimate;

  @Column(name = "error_code")
  private String errorCode;

  private String message;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Job() {}

  public static Job queued(Long storeId, JobType type) {
    Job job = new Job();
    job.id = UUID.randomUUID();
    job.storeId = storeId;
    job.type = type;
    job.status = JobStatus.QUEUED;
    return job;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void startStep(JobStep newStep) {
    this.status = JobStatus.RUNNING;
    this.step = newStep;
  }

  public void progress(int read, Integer estimate) {
    this.productsRead = read;
    this.productsEstimate = estimate;
  }

  public void succeed() {
    this.status = JobStatus.SUCCEEDED;
  }

  public void fail(ErrorCode code, String hint) {
    this.status = JobStatus.FAILED;
    this.errorCode = code.name();
    this.message = hint;
  }

  public boolean isActive() {
    return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
  }

  public UUID getId() {
    return id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public JobType getType() {
    return type;
  }

  public JobStatus getStatus() {
    return status;
  }

  public JobStep getStep() {
    return step;
  }

  public int getProductsRead() {
    return productsRead;
  }

  public Integer getProductsEstimate() {
    return productsEstimate;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getMessage() {
    return message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

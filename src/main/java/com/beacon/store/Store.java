package com.beacon.store;

import com.beacon.adapter.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** A tracked store. */
@Entity
@Table(name = "store")
public class Store {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String domain;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Platform platform;

  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StoreStatus status;

  @Column(name = "current_snapshot_id")
  private Long currentSnapshotId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Store() {}

  /** Creates a store that is being added (its first scan hasn't finished). */
  public static Store adding(String domain, String displayName, Platform platform) {
    Store store = new Store();
    store.domain = domain;
    store.displayName = displayName;
    store.platform = platform;
    store.status = StoreStatus.ADDING;
    return store;
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

  /** Marks a completed snapshot as current; called only after a successful run. */
  public void activate(Long snapshotId, String currency) {
    this.currentSnapshotId = snapshotId;
    this.currency = currency;
    this.status = StoreStatus.ACTIVE;
  }

  /** Uses the store's own name (from its home page) once it is known. */
  public void rename(String newDisplayName) {
    if (newDisplayName != null && !newDisplayName.isBlank()) {
      this.displayName =
          newDisplayName.length() > 255 ? newDisplayName.substring(0, 255) : newDisplayName;
    }
  }

  /** Marks the first scan as failed; stores with an earlier good snapshot stay active. */
  public void markFirstScanFailed() {
    if (currentSnapshotId == null) {
      this.status = StoreStatus.FAILED;
    }
  }

  public Long getId() {
    return id;
  }

  public String getDomain() {
    return domain;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Platform getPlatform() {
    return platform;
  }

  public String getCurrency() {
    return currency;
  }

  public StoreStatus getStatus() {
    return status;
  }

  public Long getCurrentSnapshotId() {
    return currentSnapshotId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

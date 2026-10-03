package com.beacon.diff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A change between two snapshots (DEVSPEC 4.6). Timing is the window between the two snapshots,
 * never an exact time: store timestamps reflect bulk syncs.
 */
@Entity
@Table(name = "change_event")
public class ChangeEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "from_snapshot_id", nullable = false)
  private Long fromSnapshotId;

  @Column(name = "to_snapshot_id", nullable = false)
  private Long toSnapshotId;

  @Column(name = "product_id")
  private Long productId;

  @Column(name = "variant_id")
  private Long variantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ChangeType type;

  @Column(name = "old_value")
  private String oldValue;

  @Column(name = "new_value")
  private String newValue;

  @Column(name = "window_start", nullable = false)
  private Instant windowStart;

  @Column(name = "window_end", nullable = false)
  private Instant windowEnd;

  protected ChangeEvent() {}

  /** Creates an event observed between two snapshots. */
  public static ChangeEvent of(
      Long storeId,
      SnapshotWindow window,
      ChangeType type,
      Long productId,
      Long variantId,
      String oldValue,
      String newValue) {
    ChangeEvent event = new ChangeEvent();
    event.storeId = storeId;
    event.fromSnapshotId = window.fromSnapshotId();
    event.toSnapshotId = window.toSnapshotId();
    event.windowStart = window.start();
    event.windowEnd = window.end();
    event.type = type;
    event.productId = productId;
    event.variantId = variantId;
    event.oldValue = oldValue;
    event.newValue = newValue;
    return event;
  }

  public Long getId() {
    return id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public Long getFromSnapshotId() {
    return fromSnapshotId;
  }

  public Long getToSnapshotId() {
    return toSnapshotId;
  }

  public Long getProductId() {
    return productId;
  }

  public Long getVariantId() {
    return variantId;
  }

  public ChangeType getType() {
    return type;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public Instant getWindowStart() {
    return windowStart;
  }

  public Instant getWindowEnd() {
    return windowEnd;
  }
}

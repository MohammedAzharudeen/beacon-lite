package com.beacon.catalog;

import com.beacon.size.SizeKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Latest known state of a variant (DEVSPEC 4.5). Matched across snapshots by external id. */
@Entity
@Table(name = "variant")
public class Variant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "external_id", nullable = false)
  private Long externalId;

  @Column(nullable = false)
  private String title;

  private String option1;
  private String option2;
  private String option3;

  @Column(name = "size_label")
  private String sizeLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "size_kind")
  private SizeKind sizeKind;

  @Column(name = "size_rank")
  private Integer sizeRank;

  @Column(name = "is_core_size", nullable = false)
  private boolean coreSize;

  private String sku;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(name = "compare_at_price", precision = 12, scale = 2)
  private BigDecimal compareAtPrice;

  @Column(nullable = false)
  private boolean available;

  @Column(nullable = false)
  private boolean removed;

  @Column(name = "last_snapshot_id", nullable = false)
  private Long lastSnapshotId;

  protected Variant() {}

  public static Variant create(Long storeId, Long productId, Long externalId) {
    Variant variant = new Variant();
    variant.storeId = storeId;
    variant.productId = productId;
    variant.externalId = externalId;
    return variant;
  }

  /** Applies the values seen in a snapshot and marks the variant as present. */
  public void apply(VariantFields fields, Long snapshotId) {
    this.title = fields.title();
    this.option1 = fields.option1();
    this.option2 = fields.option2();
    this.option3 = fields.option3();
    this.sizeLabel = fields.sizeLabel();
    this.sizeKind = fields.sizeKind();
    this.sizeRank = fields.sizeRank();
    this.coreSize = fields.coreSize();
    this.sku = fields.sku();
    this.price = fields.price();
    this.compareAtPrice = fields.compareAtPrice();
    this.available = fields.available();
    this.removed = false;
    this.lastSnapshotId = snapshotId;
  }

  /** Marks the variant as absent from the given snapshot. */
  public void markRemoved(Long snapshotId) {
    this.removed = true;
    this.lastSnapshotId = snapshotId;
  }

  public Long getId() {
    return id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public Long getProductId() {
    return productId;
  }

  public Long getExternalId() {
    return externalId;
  }

  public String getTitle() {
    return title;
  }

  public String getOption1() {
    return option1;
  }

  public String getOption2() {
    return option2;
  }

  public String getOption3() {
    return option3;
  }

  public String getSizeLabel() {
    return sizeLabel;
  }

  public SizeKind getSizeKind() {
    return sizeKind;
  }

  public Integer getSizeRank() {
    return sizeRank;
  }

  public boolean isCoreSize() {
    return coreSize;
  }

  public String getSku() {
    return sku;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public BigDecimal getCompareAtPrice() {
    return compareAtPrice;
  }

  public boolean isAvailable() {
    return available;
  }

  public boolean isRemoved() {
    return removed;
  }

  public Long getLastSnapshotId() {
    return lastSnapshotId;
  }
}

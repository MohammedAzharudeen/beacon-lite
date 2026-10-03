package com.beacon.catalog;

import com.beacon.insight.ExclusionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Latest known state of a product. Matched across snapshots by external id. */
@Entity
@Table(name = "product")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "external_id", nullable = false)
  private Long externalId;

  @Column(nullable = false)
  private String handle;

  @Column(nullable = false)
  private String title;

  @Column(name = "product_type")
  private String productType;

  private String vendor;

  // Same mapping as InsightReportEntity.payload: CLOB on H2, TEXT on PostgreSQL
  @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
  private String tags;

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name = "image_count", nullable = false)
  private int imageCount;

  @Column(name = "description_length", nullable = false)
  private int descriptionLength;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "in_best_seller_collection", nullable = false)
  private boolean inBestSellerCollection;

  @Column(nullable = false)
  private boolean promoted;

  @Enumerated(EnumType.STRING)
  private ExclusionReason exclusion;

  @Column(nullable = false)
  private boolean removed;

  @Column(name = "last_snapshot_id", nullable = false)
  private Long lastSnapshotId;

  protected Product() {}

  public static Product create(Long storeId, Long externalId) {
    Product product = new Product();
    product.storeId = storeId;
    product.externalId = externalId;
    return product;
  }

  /** Applies the values seen in a snapshot and marks the product as present. */
  public void apply(ProductFields fields, Long snapshotId) {
    this.handle = fields.handle();
    this.title = fields.title();
    this.productType = fields.productType();
    this.vendor = fields.vendor();
    this.tags = fields.tags();
    this.imageUrl = fields.imageUrl();
    this.imageCount = fields.imageCount();
    this.descriptionLength = fields.descriptionLength();
    this.publishedAt = fields.publishedAt();
    this.inBestSellerCollection = fields.inBestSellerCollection();
    this.promoted = fields.promoted();
    this.exclusion = fields.exclusion();
    this.removed = false;
    this.lastSnapshotId = snapshotId;
  }

  /** Marks the product as absent from the given snapshot. */
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

  public Long getExternalId() {
    return externalId;
  }

  public String getHandle() {
    return handle;
  }

  public String getTitle() {
    return title;
  }

  public String getProductType() {
    return productType;
  }

  public String getVendor() {
    return vendor;
  }

  public String getTags() {
    return tags;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public int getImageCount() {
    return imageCount;
  }

  public int getDescriptionLength() {
    return descriptionLength;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public boolean isInBestSellerCollection() {
    return inBestSellerCollection;
  }

  public boolean isPromoted() {
    return promoted;
  }

  public ExclusionReason getExclusion() {
    return exclusion;
  }

  public boolean isRemoved() {
    return removed;
  }

  public Long getLastSnapshotId() {
    return lastSnapshotId;
  }
}

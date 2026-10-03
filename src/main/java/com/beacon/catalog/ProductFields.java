package com.beacon.catalog;

import com.beacon.insight.ExclusionReason;
import java.time.Instant;

/** The latest known values of a product, applied to {@link Product} on each snapshot. */
public record ProductFields(
    String handle,
    String title,
    String productType,
    String vendor,
    String tags,
    String imageUrl,
    int imageCount,
    int descriptionLength,
    Instant publishedAt,
    boolean inBestSellerCollection,
    boolean promoted,
    ExclusionReason exclusion) {}

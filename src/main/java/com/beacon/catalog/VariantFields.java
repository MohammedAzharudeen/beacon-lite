package com.beacon.catalog;

import com.beacon.size.SizeKind;
import java.math.BigDecimal;

/** The latest known values of a variant, applied to {@link Variant} on each snapshot. */
public record VariantFields(
    String title,
    String option1,
    String option2,
    String option3,
    String sizeLabel,
    SizeKind sizeKind,
    Integer sizeRank,
    boolean coreSize,
    String sku,
    BigDecimal price,
    BigDecimal compareAtPrice,
    boolean available) {}

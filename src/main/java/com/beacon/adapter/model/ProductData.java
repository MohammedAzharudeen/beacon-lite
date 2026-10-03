package com.beacon.adapter.model;

import java.time.Instant;
import java.util.List;

/**
 * A product as published by the store, normalised across platforms.
 *
 * @param descriptionLength length of the description as plain text (HTML stripped)
 */
public record ProductData(
    long externalId,
    String handle,
    String title,
    String productType,
    String vendor,
    List<String> tags,
    List<String> imageUrls,
    int descriptionLength,
    Instant publishedAt,
    List<OptionData> options,
    List<VariantData> variants) {

  public ProductData {
    tags = List.copyOf(tags);
    imageUrls = List.copyOf(imageUrls);
    options = List.copyOf(options);
    variants = List.copyOf(variants);
  }

  public boolean fullySoldOut() {
    return !variants.isEmpty() && variants.stream().noneMatch(VariantData::available);
  }
}

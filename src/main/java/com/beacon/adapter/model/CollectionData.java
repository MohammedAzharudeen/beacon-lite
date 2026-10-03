package com.beacon.adapter.model;

import java.util.List;

/**
 * A store collection and the products in it.
 *
 * @param complete false when only the first pages were read
 */
public record CollectionData(
    String handle, String title, List<Long> productExternalIds, boolean complete) {

  public CollectionData {
    productExternalIds = List.copyOf(productExternalIds);
  }
}

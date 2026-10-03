package com.beacon.adapter.model;

import java.util.List;

/**
 * Collections that carry demand signals.
 *
 * @param bestSeller the store's own best-seller collections (matched by handle)
 * @param featured collections linked from the home page
 */
public record CollectionsData(List<CollectionData> bestSeller, List<CollectionData> featured) {

  public CollectionsData {
    bestSeller = List.copyOf(bestSeller);
    featured = List.copyOf(featured);
  }

  public static CollectionsData empty() {
    return new CollectionsData(List.of(), List.of());
  }
}

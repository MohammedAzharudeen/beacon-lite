package com.beacon.adapter.model;

import java.time.Instant;
import java.util.List;

/**
 * A complete, dated copy of a store's catalog and storefront signals. Written to disk as the raw
 * snapshot (gzip JSON) and replayable without network.
 *
 * @param schemaVersion file format version; readers reject unknown versions
 */
public record CatalogSnapshotData(
    int schemaVersion,
    StoreInfo store,
    Instant capturedAt,
    RobotsInfo robots,
    CatalogInfo catalog,
    List<ProductData> products,
    CollectionsData collections,
    StorefrontSignals signals) {

  public static final int SCHEMA_VERSION = 1;

  public CatalogSnapshotData {
    products = List.copyOf(products);
  }

  public CatalogSnapshotData withSignals(
      StorefrontSignals newSignals, CollectionsData newCollections, RobotsInfo newRobots) {
    return new CatalogSnapshotData(
        schemaVersion, store, capturedAt, newRobots, catalog, products, newCollections, newSignals);
  }

  public CatalogSnapshotData withStore(StoreInfo newStore) {
    return new CatalogSnapshotData(
        schemaVersion, newStore, capturedAt, robots, catalog, products, collections, signals);
  }

  public CatalogSnapshotData withCurrency(String currency) {
    return new CatalogSnapshotData(
        schemaVersion,
        new StoreInfo(store.domain(), store.platform(), currency),
        capturedAt,
        robots,
        catalog,
        products,
        collections,
        signals);
  }

  public int productCount() {
    return products.size();
  }

  public int variantCount() {
    return products.stream().mapToInt(p -> p.variants().size()).sum();
  }
}

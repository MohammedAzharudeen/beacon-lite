package com.beacon.adapter;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.fetch.FetchSession;

/**
 * Reads one kind of store. Adding a platform means adding one implementation. Every request goes
 * through the {@link FetchSession}, which obeys robots.txt.
 */
public interface StoreAdapter {

  Platform platform();

  /** True when this adapter can read the store (detection). */
  boolean supports(FetchSession session);

  /** Reads the catalog. */
  CatalogSnapshotData fetchCatalog(FetchSession session, ProgressListener progress);

  /** Reads storefront pages (home, policies, search, product pages) and adds them to the data. */
  CatalogSnapshotData fetchSignals(FetchSession session, CatalogSnapshotData catalog);
}

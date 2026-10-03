package com.beacon.adapter.model;

/**
 * How complete the catalog read was.
 *
 * @param pagesRead catalog pages read
 * @param capped true when the public feed limit (page 100, 25,000 products) was reached
 * @param sampled true when only a sample of products was read (Generic adapter)
 * @param skippedMalformed products skipped because their data couldn't be read
 */
public record CatalogInfo(int pagesRead, boolean capped, boolean sampled, int skippedMalformed) {}

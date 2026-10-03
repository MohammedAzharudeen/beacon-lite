package com.beacon.insight.report;

/**
 * Size of the catalog read.
 *
 * @param capped true when the public feed limit was hit (totals are partial)
 * @param sampled true when only a sample of products was read
 */
public record CatalogSummary(int products, int variants, boolean capped, boolean sampled) {}

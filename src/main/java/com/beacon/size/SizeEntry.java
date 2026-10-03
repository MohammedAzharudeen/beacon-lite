package com.beacon.size;

/**
 * One size of a product after analysis.
 *
 * @param rank position in the product's sorted size list (0-based)
 * @param core true for core (middle) sizes of a fit-size run
 */
public record SizeEntry(String label, SizeKind kind, int rank, boolean core) {}

package com.beacon.insight.report;

/**
 * One size of a product in the size strip.
 *
 * @param available true when any variant in this size is in stock
 */
public record SizeCell(String label, boolean available, boolean core) {}

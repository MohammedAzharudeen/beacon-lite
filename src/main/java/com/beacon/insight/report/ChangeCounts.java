package com.beacon.insight.report;

/** Changes found between the previous and the latest snapshot. */
public record ChangeCounts(int soldOut, int restocked, int priceChanges) {}

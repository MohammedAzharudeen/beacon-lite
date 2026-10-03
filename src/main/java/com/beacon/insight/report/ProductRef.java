package com.beacon.insight.report;

/** A product reference with a link to the live page. */
public record ProductRef(
    long productId, String title, String productType, String imageUrl, String productUrl) {}

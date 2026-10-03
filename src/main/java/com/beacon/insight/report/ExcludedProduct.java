package com.beacon.insight.report;

import com.beacon.insight.ExclusionReason;

/** A product kept out of restock logic, with the reason ("Not restock candidates"). */
public record ExcludedProduct(
    long productId, String title, String productUrl, ExclusionReason reason) {}

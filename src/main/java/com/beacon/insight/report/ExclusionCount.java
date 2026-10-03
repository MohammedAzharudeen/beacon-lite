package com.beacon.insight.report;

import com.beacon.insight.ExclusionReason;

/** How many products were kept out of restock logic for one reason. */
public record ExclusionCount(ExclusionReason reason, int count, String description) {}

package com.beacon.insight.report;

/**
 * The single most important finding.
 *
 * @param metric the number the sentence is built on
 * @param evidenceRef where the evidence lives in the report, e.g. {@code sizeGaps}
 */
public record Headline(String text, long metric, String evidenceRef) {}

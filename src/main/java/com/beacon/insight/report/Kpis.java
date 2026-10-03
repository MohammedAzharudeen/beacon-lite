package com.beacon.insight.report;

/**
 * The four headline numbers.
 *
 * @param sizesSoldOutPct share of all variants marked unavailable (full catalog)
 * @param changedSinceLastCheck {@code null} on the first snapshot
 */
public record Kpis(
    double sizesSoldOutPct,
    int soldOutVariants,
    EstimatedMoney atRiskPerWeek,
    Integer journeyScore,
    ChangeCounts changedSinceLastCheck) {}

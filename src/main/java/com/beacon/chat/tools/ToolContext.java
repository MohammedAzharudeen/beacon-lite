package com.beacon.chat.tools;

import com.beacon.insight.report.InsightReport;

/**
 * What a tool may read: the store (injected by the server, never chosen by the model) and its
 * report.
 */
public record ToolContext(long storeId, InsightReport report) {}

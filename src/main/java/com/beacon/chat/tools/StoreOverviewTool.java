package com.beacon.chat.tools;

import com.beacon.insight.report.InsightReport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StoreOverviewTool implements ChatTool {

  @Override
  public String name() {
    return "get_store_overview";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    InsightReport r = ctx.report();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("store", r.domain());
    out.put("dataCapturedAt", r.capturedAt());
    out.put("currency", r.currency() == null ? "unknown" : r.currency());
    out.put("headline", r.headline().text());
    out.put("products", r.catalog().products());
    out.put("variants", r.catalog().variants());
    out.put("catalogCapped", r.catalog().capped());
    out.put("sizesSoldOutPercent", r.kpis().sizesSoldOutPct());
    out.put("soldOutVariants", r.kpis().soldOutVariants());
    out.put("estimatedAtRiskPerWeek", r.kpis().atRiskPerWeek());
    out.put("productsMissingCoreSizes", r.sizeSummary().productsMissingCoreSizes());
    out.put("stylesWithOneSizeLeft", r.sizeSummary().onlyOneSizeLeft());
    out.put("journeyScore", r.journeyScore());
    out.put(
        "changesSinceLastCheck",
        r.kpis().changedSinceLastCheck() == null
            ? "Available after the next check (about 6 hours after the first one)"
            : r.kpis().changedSinceLastCheck());
    out.put("dataNotes", r.dataNotes());
    return out;
  }
}

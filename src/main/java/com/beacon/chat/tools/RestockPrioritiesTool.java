package com.beacon.chat.tools;

import com.beacon.insight.report.RestockRow;
import com.beacon.insight.report.SizeCell;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RestockPrioritiesTool implements ChatTool {

  @Override
  public String name() {
    return "get_restock_priorities";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    int limit = ChatTool.limit(args, 5, 20);
    String category = args.path("category").asText(null);
    List<Map<String, Object>> rows = new ArrayList<>();
    int rank = 0;
    int matching = 0;
    for (RestockRow r : ctx.report().restock()) {
      rank++;
      if (!ChatTool.matchesCategory(category, r.productType(), r.title())) {
        continue;
      }
      matching++;
      if (rows.size() >= limit) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("rank", rank);
      row.put("title", ChatTool.text(r.title()));
      row.put("productType", ChatTool.text(r.productType()));
      row.put(
          "soldOutSizes",
          r.sizes().stream().filter(c -> !c.available()).map(SizeCell::label).toList());
      row.put("sizesLeft", (r.totalSizes() - r.soldOutSizes()) + " of " + r.totalSizes());
      row.put("price", r.price());
      row.put("estimatedAtRiskPerWeek", r.atRiskPerWeek());
      row.put("signals", r.signals());
      row.put("confidence", r.confidence());
      row.put("productUrl", r.productUrl());
      rows.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("products", rows);
    out.put("matchingProducts", matching);
    out.put(
        "note",
        "$ at risk is an estimate from public data (price x demand x share of sizes sold out)");
    return out;
  }
}

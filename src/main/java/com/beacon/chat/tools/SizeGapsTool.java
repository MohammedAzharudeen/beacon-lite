package com.beacon.chat.tools;

import com.beacon.insight.report.SizeCell;
import com.beacon.insight.report.SizeGapRow;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SizeGapsTool implements ChatTool {

  @Override
  public String name() {
    return "get_size_gaps";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    int limit = ChatTool.limit(args, 5, 20);
    String category = args.path("category").asText(null);
    List<Map<String, Object>> rows = new ArrayList<>();
    int matching = 0;
    for (SizeGapRow g : ctx.report().sizeGaps()) {
      if (g.missingCore() == 0 || g.soldOutSizes() == g.totalSizes()) {
        continue;
      }
      if (!ChatTool.matchesCategory(category, g.productType(), g.title())) {
        continue;
      }
      matching++;
      if (rows.size() >= limit) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", ChatTool.text(g.title()));
      row.put("productType", ChatTool.text(g.productType()));
      row.put(
          "missingCoreSizes",
          g.sizes().stream().filter(c -> c.core() && !c.available()).map(SizeCell::label).toList());
      row.put("coreRange", g.coreRange());
      row.put("soldOutSizes", g.soldOutSizes());
      row.put("totalSizes", g.totalSizes());
      row.put("productUrl", g.productUrl());
      rows.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("products", rows);
    out.put("matchingProducts", matching);
    out.put(
        "productsMissingCoreSizesStoreWide", ctx.report().sizeSummary().productsMissingCoreSizes());
    return out;
  }
}

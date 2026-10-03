package com.beacon.chat.tools;

import com.beacon.insight.report.PromotedSoldOuts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PromotedSoldOutsTool implements ChatTool {

  private static final int MAX = 10;

  @Override
  public String name() {
    return "get_promoted_sold_outs";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    PromotedSoldOuts p = ctx.report().promotedSoldOuts();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("soldOutPromotedProducts", p.products().size());
    out.put("promotedProductsChecked", p.promotedChecked());
    out.put(
        "products",
        p.products().stream()
            .limit(MAX)
            .map(r -> Map.of("title", ChatTool.text(r.title()), "productUrl", r.productUrl()))
            .toList());
    if (p.notice() != null) {
      out.put("notice", p.notice());
    }
    return out;
  }
}

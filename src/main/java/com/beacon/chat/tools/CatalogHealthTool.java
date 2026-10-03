package com.beacon.chat.tools;

import com.beacon.insight.report.CatalogQuality;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CatalogHealthTool implements ChatTool {

  @Override
  public String name() {
    return "get_catalog_health";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    CatalogQuality q = ctx.report().catalogQuality();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("products", ctx.report().catalog().products());
    out.put("productsWithFewImages", q.fewImages());
    out.put("minImages", q.minImages());
    out.put("altText", q.altText().text());
    out.put("shortDescriptions", q.thinDescriptions());
    out.put("shortTitles", q.thinTitles());
    out.put("missingProductType", q.blankProductType());
    out.put("untaggedProducts", q.untagged());
    out.put("productTypesDifferingOnlyByCase", q.caseVariantTypes());
    out.put("spreadsheetErrors", q.spreadsheetErrors());
    return out;
  }
}

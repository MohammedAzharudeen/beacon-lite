package com.beacon.chat.tools;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.config.Assumptions;
import com.beacon.insight.ProductView;
import com.beacon.insight.StoreView;
import com.beacon.snapshot.CurrentCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SearchProductsTool implements ChatTool {

  private static final int MAX = 10;

  private final CurrentCatalog catalog;
  private final Assumptions assumptions;

  public SearchProductsTool(CurrentCatalog catalog, Assumptions assumptions) {
    this.catalog = catalog;
    this.assumptions = assumptions;
  }

  @Override
  public String name() {
    return "search_products";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    CatalogSnapshotData data = catalog.of(ctx.storeId());
    StoreView view = StoreView.build(data, assumptions, Set.of(), false);
    String query = lower(args.path("query").asText(""));
    String category = args.path("category").asText(null);
    BigDecimal min = args.hasNonNull("min_price") ? args.get("min_price").decimalValue() : null;
    BigDecimal max = args.hasNonNull("max_price") ? args.get("max_price").decimalValue() : null;
    Boolean inStock = args.hasNonNull("in_stock") ? args.get("in_stock").asBoolean() : null;
    String size = args.hasNonNull("size") ? args.get("size").asText().trim() : null;
    Boolean discounted = args.hasNonNull("discounted") ? args.get("discounted").asBoolean() : null;
    List<Map<String, Object>> rows = new ArrayList<>();
    int matching = 0;
    for (ProductView v : view.products()) {
      ProductData p = v.product();
      if (!query.isBlank()
          && !lower(p.title()).contains(query)
          && !lower(p.productType()).contains(query)) {
        continue;
      }
      if (!ChatTool.matchesCategory(category, p.productType(), p.title())) {
        continue;
      }
      BigDecimal price = v.price();
      if ((min != null && price.compareTo(min) < 0) || (max != null && price.compareTo(max) > 0)) {
        continue;
      }
      boolean anyAvailable = p.variants().stream().anyMatch(VariantData::available);
      if (inStock != null && inStock != anyAvailable) {
        continue;
      }
      Map<String, Boolean> sizes = v.sizeAvailability();
      if (size != null && !Boolean.TRUE.equals(sizes.get(size))) {
        continue;
      }
      boolean isDiscounted =
          p.variants().stream()
              .anyMatch(
                  x -> x.compareAtPrice() != null && x.compareAtPrice().compareTo(x.price()) > 0);
      if (discounted != null && discounted != isDiscounted) {
        continue;
      }
      matching++;
      if (rows.size() < MAX) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", ChatTool.text(p.title()));
        row.put("productType", ChatTool.text(p.productType()));
        row.put("price", price);
        row.put("discounted", isDiscounted);
        row.put(
            "sizesAvailable",
            sizes.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList());
        row.put(
            "sizesSoldOut",
            sizes.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList());
        row.put("productUrl", v.productUrl());
        rows.add(row);
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("matchingProducts", matching);
    out.put("currency", data.store().currency() == null ? "unknown" : data.store().currency());
    out.put("products", rows);
    return out;
  }

  private static String lower(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT);
  }
}

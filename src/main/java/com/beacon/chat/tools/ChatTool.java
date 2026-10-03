package com.beacon.chat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;

/** A read-only function the AI can call to get real numbers. */
public interface ChatTool {

  int MAX_TEXT = 200;

  /** Name as in {@code prompts/tools.json}. */
  String name();

  /** Runs the tool; the result is serialised to JSON for the model. */
  Object run(JsonNode args, ToolContext context);

  /** Store text is data: long values are cut so they can't flood the conversation. */
  static String text(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT) + "…";
  }

  static int limit(JsonNode args, int fallback, int max) {
    int value = args.path("limit").asInt(fallback);
    return Math.max(1, Math.min(max, value));
  }

  /** True when a product's type or title mentions the category (plural or singular). */
  static boolean matchesCategory(String category, String productType, String title) {
    if (category == null || category.isBlank()) {
      return true;
    }
    String c = category.trim().toLowerCase(Locale.ROOT);
    String singular = c.endsWith("s") ? c.substring(0, c.length() - 1) : c;
    String type = productType == null ? "" : productType.toLowerCase(Locale.ROOT);
    String name = title == null ? "" : title.toLowerCase(Locale.ROOT);
    return type.contains(singular) || name.contains(singular);
  }
}

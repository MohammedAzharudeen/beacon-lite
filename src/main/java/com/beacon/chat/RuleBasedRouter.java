package com.beacon.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Answers without the AI model (model off or slow), and builds the safe answer when a model's draft
 * fails the number check: keyword routing to one tool, then a template filled only from that tool's
 * JSON.
 */
@Component
public class RuleBasedRouter {

  private static final Pattern MAX_PRICE =
      Pattern.compile("(?:under|below|less than|cheaper than)\\s*[$€£]?\\s?(\\d+(?:\\.\\d+)?)");
  private static final Pattern MIN_PRICE =
      Pattern.compile("(?:over|above|more than)\\s*[$€£]?\\s?(\\d+(?:\\.\\d+)?)");
  private static final Pattern SIZE =
      Pattern.compile("\\bsize\\s+([0-9]{1,2}(?:\\.5)?|xxs|xs|s|m|l|xl|xxl)\\b");
  private static final Pattern CATEGORY =
      Pattern.compile(
          "\\b(boots?|sneakers?|sandals?|heels?|loafers?|flats?|mules?|dresses|dress|tops?|bags?|handbags?|"
              + "jackets?|shoes?|pants|shorts|skirts?|sets?|hoodies?|t-shirts?|accessories|jeans|knitwear|socks|hats?)\\b");

  private final ObjectMapper mapper;

  public RuleBasedRouter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** The tool to call and its arguments. */
  public record Route(String tool, String arguments) {}

  public Route route(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    ObjectNode args = mapper.createObjectNode();
    String category = match(CATEGORY, q);
    boolean compareAt = q.matches(".*\\bcompare[- ]at\\b.*");
    if (!compareAt
        && q.matches(
            ".*\\b(compare|comparison|versus|vs\\.?|benchmark|other stores?|competitors?)\\b.*")) {
      return new Route("compare_stores", "{}");
    }
    if (q.matches(
        ".*\\b(sold out since|what sold out|just sold out|changed|changes|since yesterday|since last|today|"
            + "came back|restocked recently|new arrivals?|trending|trend)\\b.*")) {
      return new Route("get_recent_changes", "{}");
    }
    if (q.matches(".*\\b(restock|re-stock|reorder|re-order|replenish)\\b.*")) {
      args.put("limit", 5);
      if (category != null) {
        args.put("category", category);
      }
      return new Route("get_restock_priorities", args.toString());
    }
    boolean search =
        q.matches(".*\\b(find|show me|list|which products|search for|any)\\b.*")
            && (MAX_PRICE.matcher(q).find()
                || MIN_PRICE.matcher(q).find()
                || SIZE.matcher(q).find()
                || q.contains("in stock")
                || q.contains("discount")
                || q.contains("on sale"));
    if (q.matches(
            ".*\\b(core sizes?|sizes? missing|missing sizes?|size gaps?|size runs?|which sizes)\\b.*")
        && !search) {
      args.put("limit", 5);
      if (category != null) {
        args.put("category", category);
      }
      return new Route("get_size_gaps", args.toString());
    }
    if (search) {
      if (category != null) {
        args.put("category", category);
      }
      String max = match(MAX_PRICE, q);
      if (max != null) {
        args.put("max_price", Double.parseDouble(max));
      }
      String min = match(MIN_PRICE, q);
      if (min != null) {
        args.put("min_price", Double.parseDouble(min));
      }
      String size = match(SIZE, q);
      if (size != null) {
        args.put("size", size.toUpperCase(Locale.ROOT));
      }
      if (q.contains("in stock")) {
        args.put("in_stock", true);
      }
      if (q.contains("discount") || q.contains("on sale")) {
        args.put("discounted", true);
      }
      return new Route("search_products", args.toString());
    }
    if (q.matches(".*\\b(promot\\w*|home ?page|featured)\\b.*")) {
      return new Route("get_promoted_sold_outs", "{}");
    }
    if (q.matches(
        ".*\\b(price|pricing|discounts?|compare-at|compare at|markdowns?|free shipping|dead stock)\\b.*")) {
      return new Route("get_pricing_insights", "{}");
    }
    if (q.matches(
        ".*\\b(catalog|catalogue|images?|photos?|alt text|descriptions?|product types?|titles?)\\b.*")) {
      return new Route("get_catalog_health", "{}");
    }
    if (q.matches(
        ".*\\b(journey|friction|losing shoppers|where am i losing|lose shoppers|search|returns?|checkout|"
            + "wishlist|notify|size guide|page speed|slow|fix first)\\b.*")) {
      String stage = stageFor(q);
      if (stage != null) {
        args.put("stage", stage);
      }
      return new Route("get_journey_friction", args.toString());
    }
    return new Route("get_store_overview", "{}");
  }

  /** A plain-language answer built only from the tool result. */
  public String answer(ToolResult result) {
    JsonNode d = result.data();
    return switch (result.toolName()) {
      case "get_restock_priorities" -> restock(d);
      case "get_size_gaps" -> sizeGaps(d);
      case "get_recent_changes" -> changes(d);
      case "get_promoted_sold_outs" -> promoted(d);
      case "get_pricing_insights" -> pricing(d);
      case "get_catalog_health" -> catalog(d);
      case "get_journey_friction" -> journey(d);
      case "search_products" -> search(d);
      case "compare_stores" -> compare(d);
      default -> overview(d);
    };
  }

  private static String restock(JsonNode d) {
    JsonNode rows = d.path("products");
    if (rows.isEmpty()) {
      return "No products need restocking under that filter: none have a fully sold-out size and a demand signal.";
    }
    StringBuilder out =
        new StringBuilder("Restock these first (ranked by estimated $ at risk per week):\n");
    int i = 1;
    for (JsonNode r : rows) {
      out.append(i++)
          .append(". ")
          .append(r.path("title").asText())
          .append(": ")
          .append(r.path("sizesLeft").asText())
          .append(" sizes left, about ")
          .append(whole(r.path("estimatedAtRiskPerWeek").path("amount").asText()))
          .append(' ')
          .append(currency(r.path("estimatedAtRiskPerWeek")))
          .append(" a week at risk (estimate), confidence ")
          .append(r.path("confidence").asText().toLowerCase(Locale.ROOT))
          .append(".\n");
    }
    return out.toString().trim();
  }

  private static String sizeGaps(JsonNode d) {
    JsonNode rows = d.path("products");
    StringBuilder out =
        new StringBuilder(d.path("productsMissingCoreSizesStoreWide").asText())
            .append(" products are missing core sizes while other sizes are in stock.");
    if (rows.isEmpty()) {
      return out.append(" None match that filter.").toString();
    }
    out.append(" For example:\n");
    int i = 1;
    for (JsonNode r : rows) {
      List<String> sizes = new ArrayList<>();
      r.path("missingCoreSizes").forEach(s -> sizes.add(s.asText()));
      out.append(i++)
          .append(". ")
          .append(r.path("title").asText())
          .append(": missing sizes ")
          .append(String.join(", ", sizes))
          .append(".\n");
    }
    return out.toString().trim();
  }

  private static String changes(JsonNode d) {
    if ("NOT_AVAILABLE".equals(d.path("status").asText())) {
      return d.path("reason").asText()
          + ". Trends such as \"what's trending\" need a few days of snapshots.";
    }
    JsonNode c = d.path("counts");
    StringBuilder out =
        new StringBuilder("Since the last check: ")
            .append(c.path("soldOut").asInt())
            .append(" sizes sold out, ")
            .append(c.path("restocked").asInt())
            .append(" came back in stock and ")
            .append(c.path("priceChanges").asInt())
            .append(" prices changed.");
    JsonNode window = d.path("window");
    if (!window.isMissingNode() && !window.isNull()) {
      out.append(" Window: ")
          .append(window.path("start").asText())
          .append(" to ")
          .append(window.path("end").asText())
          .append('.');
    }
    return out.toString();
  }

  private static String promoted(JsonNode d) {
    int n = d.path("soldOutPromotedProducts").asInt();
    StringBuilder out = new StringBuilder(n + " fully sold-out products are still promoted.");
    if (d.has("notice")) {
      out.append(' ').append(d.path("notice").asText()).append('.');
    }
    return out.toString();
  }

  private static String pricing(JsonNode d) {
    StringBuilder out =
        new StringBuilder()
            .append(d.path("compareAtEqualsPrice").asInt())
            .append(
                " products have a compare-at price equal to the selling price (catalog clean-up). ")
            .append(d.path("discounted").asInt())
            .append(" products are discounted; ")
            .append(d.path("deepDiscount").asInt())
            .append(" by ")
            .append(d.path("deepDiscountPercent").asInt())
            .append("% or more.");
    JsonNode fs = d.path("freeShipping");
    if (fs.hasNonNull("threshold")) {
      out.append(" Free shipping starts at ")
          .append(fs.path("threshold").asText())
          .append(", against a median product price of ")
          .append(fs.path("medianPrice").asText())
          .append('.');
    } else {
      out.append(" Free-shipping threshold: not found.");
    }
    return out.toString();
  }

  private static String catalog(JsonNode d) {
    return d.path("productsWithFewImages").asInt()
        + " of "
        + d.path("products").asInt()
        + " products have fewer than "
        + d.path("minImages").asInt()
        + " images. "
        + d.path("altText").asText()
        + ". "
        + d.path("shortDescriptions").asInt()
        + " products have short descriptions and "
        + d.path("missingProductType").asInt()
        + " have no product type.";
  }

  private static String journey(JsonNode d) {
    StringBuilder out = new StringBuilder();
    if (d.hasNonNull("overallJourneyScore")) {
      out.append("Journey score ")
          .append(d.path("overallJourneyScore").asInt())
          .append(" out of 100");
      if (d.hasNonNull("weakestStage")) {
        out.append("; the weakest stage is ").append(d.path("weakestStage").asText());
      }
      out.append(".");
    }
    for (JsonNode stage : d.path("stages")) {
      for (JsonNode check : stage.path("checks")) {
        String status = check.path("status").asText();
        if (status.equals("NOT_CHECKED_ROBOTS")) {
          out.append(' ')
              .append(check.path("label").asText())
              .append(": not checked on this store; its robots.txt blocks it.");
        } else if (check.hasNonNull("fix")) {
          out.append(' ')
              .append(check.path("label").asText())
              .append(" (")
              .append(check.path("score").asInt())
              .append("): ")
              .append(check.path("fix").asText())
              .append('.');
        }
      }
    }
    return out.toString().trim();
  }

  private static String search(JsonNode d) {
    int n = d.path("matchingProducts").asInt();
    if (n == 0) {
      return "No products match.";
    }
    StringBuilder out = new StringBuilder(n + " products match. First results:\n");
    int i = 1;
    for (JsonNode r : d.path("products")) {
      out.append(i++)
          .append(". ")
          .append(r.path("title").asText())
          .append(", ")
          .append(r.path("price").asText())
          .append(' ')
          .append(d.path("currency").asText())
          .append(".\n");
    }
    return out.toString().trim();
  }

  private static String compare(JsonNode d) {
    StringBuilder out = new StringBuilder("Compared on checks that ran on every store:\n");
    for (JsonNode s : d.path("stores")) {
      out.append("- ")
          .append(s.path("domain").asText())
          .append(": ")
          .append(s.path("sizesSoldOutPct").asText())
          .append("% of sizes sold out, journey score ")
          .append(s.path("comparableJourneyScore").asText("n/a"))
          .append(", about ")
          .append(whole(s.path("atRiskPerWeek").asText()))
          .append(' ')
          .append(s.path("currency").asText(""))
          .append(" a week at risk (estimate).\n");
    }
    return out.toString().trim();
  }

  private static String overview(JsonNode d) {
    return d.path("headline").asText()
        + " "
        + d.path("sizesSoldOutPercent").asText()
        + "% of sizes are sold out across "
        + d.path("products").asInt()
        + " products; journey score "
        + d.path("journeyScore").asText("n/a")
        + ".";
  }

  /**
   * Whole amount with thousands separators, e.g. 2089.48 → 2,089 (the number check allows
   * rounding).
   */
  private static String whole(String amount) {
    try {
      return NumberFormat.getIntegerInstance(Locale.US).format(new BigDecimal(amount));
    } catch (NumberFormatException e) {
      return amount;
    }
  }

  private static String currency(JsonNode money) {
    return money.hasNonNull("currency") ? money.path("currency").asText() : "(currency unknown)";
  }

  private static String stageFor(String q) {
    if (q.matches(".*\\b(search|find|browse)\\b.*")) {
      return "BROWSE";
    }
    if (q.matches(".*\\b(returns?|checkout|shipping|pay)\\b.*")) {
      return "CART_CHECKOUT";
    }
    if (q.matches(".*\\b(wishlist|notify|come back|email)\\b.*")) {
      return "COME_BACK";
    }
    if (q.matches(".*\\b(size guide|images?|reviews?)\\b.*")) {
      return "PRODUCT_PAGE";
    }
    if (q.matches(".*\\b(page speed|slow|home ?page)\\b.*")) {
      return "DISCOVER";
    }
    return null;
  }

  private static String match(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(1) : null;
  }
}

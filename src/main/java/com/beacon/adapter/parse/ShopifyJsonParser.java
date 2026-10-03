package com.beacon.adapter.parse;

import com.beacon.adapter.model.OptionData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.SearchSignals.SearchProbe;
import com.beacon.adapter.model.VariantData;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns Shopify's public JSON ({@code /products.json}, {@code /collections.json}, {@code
 * /products/<handle>.json}, {@code /search/suggest.json}) into normalised records. A malformed
 * product is logged and skipped; it never fails the scan.
 */
public final class ShopifyJsonParser {

  private static final Logger log = LoggerFactory.getLogger(ShopifyJsonParser.class);
  private static final int TOP_TITLES = 3;

  private final ObjectMapper mapper;

  public ShopifyJsonParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Products on one catalog or collection page. */
  public ProductPage parseProducts(String json) {
    JsonNode products = read(json).path("products");
    if (!products.isArray()) {
      throw new BeaconException(
          ErrorCode.PARSE_FAILED, "No products array in the catalog feed", "Try again later", null);
    }
    List<ProductData> parsed = new ArrayList<>();
    int skipped = 0;
    for (JsonNode node : products) {
      try {
        parsed.add(product(node));
      } catch (RuntimeException e) {
        skipped++;
        log.warn(
            "[PARSE] product id={} skipped reason={}", node.path("id").asText("?"), e.getMessage());
      }
    }
    return new ProductPage(parsed, skipped);
  }

  /**
   * True when the body is JSON with a {@code products} array (how a Shopify feed is recognised).
   */
  public boolean isProductFeed(String json) {
    try {
      return mapper.readTree(json).path("products").isArray();
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  /** Handles and titles on one {@code /collections.json} page. */
  public List<CollectionRef> parseCollections(String json) {
    List<CollectionRef> refs = new ArrayList<>();
    for (JsonNode node : read(json).path("collections")) {
      String handle = node.path("handle").asText("");
      if (!handle.isEmpty()) {
        refs.add(new CollectionRef(handle, node.path("title").asText(handle)));
      }
    }
    return refs;
  }

  /** Image alt text of one product from {@code /products/<handle>.json}. */
  public AltCount parseAltText(String json) {
    int images = 0;
    int withAlt = 0;
    for (JsonNode image : read(json).path("product").path("images")) {
      images++;
      if (!image.path("alt").asText("").isBlank()) {
        withAlt++;
      }
    }
    return new AltCount(images, withAlt);
  }

  /** Products returned by {@code /search/suggest.json} for one term. */
  public SearchProbe parseSearch(String json, String term) {
    String needle = term.toLowerCase(Locale.ROOT);
    List<String> titles = new ArrayList<>();
    int relevant = 0;
    JsonNode products = read(json).path("resources").path("results").path("products");
    for (JsonNode p : products) {
      String title = p.path("title").asText("");
      String type = p.has("type") ? p.path("type").asText("") : p.path("product_type").asText("");
      titles.add(title);
      if (title.toLowerCase(Locale.ROOT).contains(needle)
          || type.toLowerCase(Locale.ROOT).contains(needle)) {
        relevant++;
      }
    }
    return new SearchProbe(
        term, titles.size(), relevant, titles.subList(0, Math.min(TOP_TITLES, titles.size())));
  }

  private ProductData product(JsonNode node) {
    long id = requiredLong(node, "id");
    List<OptionData> options = new ArrayList<>();
    for (JsonNode option : node.path("options")) {
      List<String> values = new ArrayList<>();
      option.path("values").forEach(v -> values.add(v.asText()));
      options.add(
          new OptionData(option.path("name").asText(""), option.path("position").asInt(), values));
    }
    List<VariantData> variants = new ArrayList<>();
    for (JsonNode v : node.path("variants")) {
      variants.add(
          new VariantData(
              requiredLong(v, "id"),
              v.path("title").asText(""),
              text(v, "option1"),
              text(v, "option2"),
              text(v, "option3"),
              text(v, "sku"),
              v.path("available").asBoolean(false),
              money(v.path("price"), true),
              money(v.path("compare_at_price"), false)));
    }
    List<String> images = new ArrayList<>();
    node.path("images").forEach(img -> images.add(img.path("src").asText()));
    String body = node.path("body_html").asText("");
    return new ProductData(
        id,
        node.path("handle").asText(""),
        node.path("title").asText(""),
        text(node, "product_type"),
        text(node, "vendor"),
        tags(node.path("tags")),
        images,
        body.isEmpty() ? 0 : Jsoup.parse(body).text().length(),
        instant(text(node, "published_at")),
        options,
        variants);
  }

  private static List<String> tags(JsonNode tags) {
    List<String> out = new ArrayList<>();
    if (tags.isArray()) {
      tags.forEach(t -> out.add(t.asText().trim()));
    } else if (tags.isTextual() && !tags.asText().isBlank()) {
      Arrays.stream(tags.asText().split(",")).map(String::trim).forEach(out::add);
    }
    out.removeIf(String::isEmpty);
    return out;
  }

  private static long requiredLong(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !(value.isNumber() || value.isTextual())) {
      throw new IllegalArgumentException("missing " + field);
    }
    return value.isNumber() ? value.asLong() : Long.parseLong(value.asText());
  }

  private static BigDecimal money(JsonNode value, boolean required) {
    if (value == null || value.isNull() || value.isMissingNode() || value.asText().isBlank()) {
      if (required) {
        throw new IllegalArgumentException("missing price");
      }
      return null;
    }
    return new BigDecimal(value.asText().trim());
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static Instant instant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private JsonNode read(String json) {
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new BeaconException(
          ErrorCode.PARSE_FAILED, "The store returned JSON we couldn't read", "Try again later", e);
    }
  }

  /** Products parsed from one page and how many were skipped. */
  public record ProductPage(List<ProductData> products, int skipped) {}

  /** A collection's handle and title. */
  public record CollectionRef(String handle, String title) {}

  /** Image alt text counts for one product. */
  public record AltCount(int images, int withAlt) {}
}

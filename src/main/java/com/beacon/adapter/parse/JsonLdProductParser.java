package com.beacon.adapter.parse;

import com.beacon.adapter.model.OptionData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * Reads schema.org Product data (JSON-LD) from server-rendered product pages, for stores without a
 * catalog feed. Only offers with both a price and an availability become variants; a product
 * without such offers has no variants, which reports show as "no readable stock data" instead of
 * guessing.
 */
public final class JsonLdProductParser {

  private final ObjectMapper mapper;

  public JsonLdProductParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** The product on a page, or empty when the page has no schema.org Product. */
  public Optional<ProductData> parse(String html, String pageUrl) {
    for (Element script : Jsoup.parse(html).select("script[type=application/ld+json]")) {
      Optional<JsonNode> product = read(script.data()).flatMap(this::findProduct);
      if (product.isPresent()) {
        Optional<ProductData> data = toProduct(product.get(), pageUrl);
        if (data.isPresent()) {
          return data;
        }
      }
    }
    return Optional.empty();
  }

  private Optional<JsonNode> findProduct(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return Optional.empty();
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        Optional<JsonNode> found = findProduct(child);
        if (found.isPresent()) {
          return found;
        }
      }
      return Optional.empty();
    }
    if (isType(node, "Product") || isType(node, "ProductGroup")) {
      return Optional.of(node);
    }
    return findProduct(node.get("@graph"));
  }

  private Optional<ProductData> toProduct(JsonNode product, String pageUrl) {
    List<JsonNode> offers = new ArrayList<>();
    collectOffers(product.get("offers"), offers);
    product.path("hasVariant").forEach(v -> collectOffers(v.get("offers"), offers));
    List<VariantData> variants = new ArrayList<>();
    long productId = stableId(pageUrl);
    int index = 0;
    for (JsonNode offer : offers) {
      String price = offer.path("price").asText("");
      String availability = offer.path("availability").asText("");
      if (price.isBlank() || availability.isBlank()) {
        continue;
      }
      String sku = offer.path("sku").asText(product.path("sku").asText(null));
      String name = offer.path("name").asText(null);
      variants.add(
          new VariantData(
              stableId(pageUrl + "#" + index++),
              name == null ? "Default" : name,
              name,
              null,
              null,
              sku,
              availability.contains("InStock") || availability.contains("LimitedAvailability"),
              new BigDecimal(price.trim()),
              null));
    }
    List<String> images = new ArrayList<>();
    JsonNode image = product.get("image");
    if (image != null && image.isArray()) {
      image.forEach(i -> images.add(i.isTextual() ? i.asText() : i.path("url").asText()));
    } else if (image != null) {
      images.add(image.isTextual() ? image.asText() : image.path("url").asText());
    }
    String path = pageUrl.replaceFirst("^https?://[^/]+", "");
    String handle = path.substring(path.lastIndexOf('/') + 1);
    return Optional.of(
        new ProductData(
            productId,
            handle,
            product.path("name").asText(handle),
            product.path("category").isTextual() ? product.path("category").asText() : null,
            product.path("brand").path("name").asText(null),
            List.of(),
            images,
            Jsoup.parse(product.path("description").asText("")).text().length(),
            null,
            List.<OptionData>of(),
            variants));
  }

  private static void collectOffers(JsonNode offers, List<JsonNode> out) {
    if (offers == null) {
      return;
    }
    if (offers.isArray()) {
      offers.forEach(o -> collectOffers(o, out));
    } else if (isType(offers, "AggregateOffer") && offers.has("offers")) {
      collectOffers(offers.get("offers"), out);
    } else if (offers.isObject()) {
      out.add(offers);
    }
  }

  private static boolean isType(JsonNode node, String type) {
    JsonNode t = node.get("@type");
    if (t == null) {
      return false;
    }
    if (t.isArray()) {
      for (JsonNode each : t) {
        if (type.equals(each.asText())) {
          return true;
        }
      }
      return false;
    }
    return type.equals(t.asText());
  }

  /** Generic stores have no numeric ids, so a stable id is derived from the URL. */
  static long stableId(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits()
        & Long.MAX_VALUE;
  }

  private Optional<JsonNode> read(String json) {
    try {
      return Optional.ofNullable(mapper.readTree(json));
    } catch (JsonProcessingException e) {
      return Optional.empty(); // broken JSON-LD blocks are common; the page is simply skipped
    }
  }
}

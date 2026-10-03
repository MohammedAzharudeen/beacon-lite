package com.beacon.adapter.parse;

import com.beacon.config.Assumptions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/**
 * Reads storefront HTML without running JavaScript: links, apps, feature text, currency and policy
 * wording. What only appears after JavaScript runs is not seen, and reports say so.
 */
public final class StorefrontHtmlParser {

  private static final Pattern PRODUCT_LINK =
      Pattern.compile(
          "href=\"(?:https?://[^\"/]+)?(?:/[a-z]{2}(?:-[a-zA-Z]{2})?)?(?:/collections/[^\"/]+)?"
              + "/products/([A-Za-z0-9][A-Za-z0-9\\-_%.]*)[\"?#]");
  // Same patterns as scripts/record-stores.py so live scans and recordings pick the same pages
  private static final Pattern COLLECTION_LINK =
      Pattern.compile("href=\"(?:https://[^\"/]+)?/collections/([a-z0-9][a-z0-9\\-]*)\"");
  private static final Pattern PAGE_LINK =
      Pattern.compile("href=\"(?:https://[^\"/]+)?/pages/([a-z0-9][a-z0-9\\-]*)\"");
  private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern SHOPIFY_CURRENCY =
      Pattern.compile("Shopify\\.currency\\s*=\\s*\\{\\s*\"active\"\\s*:\\s*\"([A-Z]{3})\"");
  private static final String MONEY =
      "(?:[A-Z]{3}\\s?)?[$€£]\\s?(\\d{1,5}(?:[.,]\\d{2})?)\\+?\\*?(?:\\s?(?:USD|EUR|GBP|CAD|AUD))?";
  // "Free shipping on orders over $80", "Free Shipping on orders $75+"
  private static final Pattern FREE_SHIPPING_FROM =
      Pattern.compile(
          "free\\s+(?:standard\\s+|ground\\s+|us\\s+)?shipping\\s+(?:on|for)?\\s*(?:all\\s+)?(?:orders|purchases)?\\s*"
              + "(?:over|above|of|from)?\\s*"
              + MONEY,
          Pattern.CASE_INSENSITIVE);
  // "Spend $75.00 USD more for free shipping", "$75+ ships free"
  private static final Pattern SPEND_FOR_FREE_SHIPPING =
      Pattern.compile(
          "(?:spend\\s+|orders\\s+(?:over|above)\\s+)?"
              + MONEY
              + "\\s*(?:or\\s+more\\s+)?(?:more\\s+)?(?:to\\s+get\\s+|for\\s+|get\\s+|and\\s+get\\s+)?free\\s+(?:standard\\s+)?shipping",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern FREE_SHIPPING =
      Pattern.compile(
          "free\\s+(?:standard\\s+|ground\\s+|us\\s+)?shipping", Pattern.CASE_INSENSITIVE);
  // Returns windows: "within 30 days of delivery", "30 days from delivery", "30-day returns".
  // Processing times ("3-6 business days to process your refund") are deliberately not matched.
  private static final List<Pattern> RETURN_WINDOW =
      List.of(
          Pattern.compile(
              "within\\s+(\\d{1,3})\\s*(?:calendar\\s+)?days?\\s+(?:of|from|after)\\b",
              Pattern.CASE_INSENSITIVE),
          Pattern.compile(
              "(\\d{1,3})\\s*(?:calendar\\s+)?days?\\s+(?:from|of|after)\\s+(?:the\\s+)?(?:date\\s+of\\s+)?"
                  + "(?:delivery|receipt|purchase|shipment|order|the\\s+order)",
              Pattern.CASE_INSENSITIVE),
          Pattern.compile(
              "(\\d{1,3})[- ]day\\s+(?:return|refund|exchange)", Pattern.CASE_INSENSITIVE));
  private static final Pattern RETURN_WORD =
      Pattern.compile("return|refund|exchange", Pattern.CASE_INSENSITIVE);
  private static final Pattern FREE_RETURNS =
      Pattern.compile(
          "free returns?|returns? (?:are|is) free|free (?:return|exchange) shipping|free exchanges?",
          Pattern.CASE_INSENSITIVE);
  private static final int MAX_EVIDENCE_CHARS = 160;

  private final Assumptions.Detection detection;
  private final Map<String, Pattern> appPatterns;
  private final Pattern notifyMe;
  private final Pattern sizeGuide;

  public StorefrontHtmlParser(Assumptions.Detection detection) {
    this.detection = detection;
    Map<String, Pattern> apps = new LinkedHashMap<>();
    detection.apps().forEach((k, v) -> apps.put(k, Pattern.compile(v, Pattern.CASE_INSENSITIVE)));
    this.appPatterns = apps;
    this.notifyMe = Pattern.compile(detection.notifyMeText(), Pattern.CASE_INSENSITIVE);
    this.sizeGuide = Pattern.compile(detection.sizeGuideText(), Pattern.CASE_INSENSITIVE);
  }

  public boolean looksLikeShopify(String html) {
    return Pattern.compile(detection.shopifyMarkers(), Pattern.CASE_INSENSITIVE)
        .matcher(html)
        .find();
  }

  public List<String> productHandles(String html) {
    return distinct(PRODUCT_LINK.matcher(html));
  }

  public List<String> collectionHandles(String html) {
    return distinct(COLLECTION_LINK.matcher(html));
  }

  /**
   * {@code /pages/*} handles whose name matches the returns or shipping patterns, in page order.
   */
  public List<String> policyPageHandles(String html) {
    Pattern topic =
        Pattern.compile(
            detection.returnsPages() + "|" + detection.shippingPages(), Pattern.CASE_INSENSITIVE);
    List<String> out = new ArrayList<>();
    for (String handle : distinct(PAGE_LINK.matcher(html))) {
      if (topic.matcher(handle).find()) {
        out.add(handle);
      }
    }
    return out;
  }

  public boolean isReturnsPage(String handle) {
    return Pattern.compile(detection.returnsPages(), Pattern.CASE_INSENSITIVE)
        .matcher(handle)
        .find();
  }

  public boolean isShippingPage(String handle) {
    return Pattern.compile(detection.shippingPages(), Pattern.CASE_INSENSITIVE)
        .matcher(handle)
        .find();
  }

  public int scriptCount(String html) {
    Matcher m = SCRIPT_TAG.matcher(html);
    int count = 0;
    while (m.find()) {
      count++;
    }
    return count;
  }

  /** Keys of {@code detection.apps} whose pattern appears in the HTML. */
  public Set<String> apps(String html) {
    Set<String> found = new TreeSet<>();
    appPatterns.forEach(
        (name, pattern) -> {
          if (pattern.matcher(html).find()) {
            found.add(name);
          }
        });
    return found;
  }

  public boolean hasNotifyMeText(String html) {
    return notifyMe.matcher(html).find();
  }

  public boolean hasSizeGuide(String html) {
    return sizeGuide.matcher(html).find();
  }

  /** Store currency from {@code Shopify.currency}, or {@code null} when not present. */
  public String currency(String html) {
    Matcher m = SHOPIFY_CURRENCY.matcher(html);
    return m.find() ? m.group(1) : null;
  }

  /** The store's name from {@code <meta property="og:site_name">}, or {@code null}. */
  public String siteName(String html) {
    String name = Jsoup.parse(html).select("meta[property=og:site_name]").attr("content").strip();
    return name.isEmpty() ? null : name;
  }

  /** Visible text of a page (scripts and styles dropped). */
  public String text(String html) {
    return Jsoup.parse(html).text();
  }

  /** First free-shipping offer and its amount; the amount is null when none is stated. */
  public FreeShipping freeShipping(String text) {
    for (Pattern pattern : List.of(FREE_SHIPPING_FROM, SPEND_FOR_FREE_SHIPPING)) {
      Matcher m = pattern.matcher(text);
      if (m.find()) {
        return new FreeShipping(evidence(m.group()), new BigDecimal(m.group(1).replace(',', '.')));
      }
    }
    Matcher any = FREE_SHIPPING.matcher(text);
    if (any.find()) {
      int end = Math.min(text.length(), any.end() + 60);
      return new FreeShipping(evidence(text.substring(any.start(), end)), null);
    }
    return null;
  }

  /** Returns window in days, from the first sentence about returns that states one. */
  public Integer returnDays(String text) {
    for (String sentence : text.split("(?<=[.!?])\\s+")) {
      if (!RETURN_WORD.matcher(sentence).find()) {
        continue;
      }
      for (Pattern pattern : RETURN_WINDOW) {
        Matcher m = pattern.matcher(sentence);
        if (m.find()) {
          int value = Integer.parseInt(m.group(1));
          if (value > 0 && value <= 365) {
            return value;
          }
        }
      }
    }
    return null;
  }

  /** True when the text says returns are free; {@code null} when it doesn't say. */
  public Boolean freeReturns(String text) {
    return FREE_RETURNS.matcher(text).find() ? Boolean.TRUE : null;
  }

  private static String evidence(String raw) {
    String compact = raw.replaceAll("\\s+", " ").trim();
    return compact.length() <= MAX_EVIDENCE_CHARS
        ? compact
        : compact.substring(0, MAX_EVIDENCE_CHARS);
  }

  private static List<String> distinct(Matcher m) {
    Set<String> out = new LinkedHashSet<>();
    while (m.find()) {
      out.add(m.group(1));
    }
    return List.copyOf(out);
  }

  /** A free-shipping offer; {@code threshold} is null when no amount is stated. */
  public record FreeShipping(String text, BigDecimal threshold) {}
}

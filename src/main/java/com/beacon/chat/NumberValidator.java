package com.beacon.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks that every number in an AI answer appears in the tool results.
 *
 * <ul>
 *   <li>Facts are numbers the app computed: numeric fields and our own text (evidence, notes).
 *       Store text (titles, product types, store-written sentences) is data and never counts as a
 *       fact, so an instruction hidden in a product title can't smuggle a number into an answer.
 *   <li>Product titles and size labels are removed from the answer before checking ("size 9" is not
 *       a quantity; "Club C 85" is a name).
 *   <li>Money, percentages and counts may be rounded: within ±1 in the last digit shown.
 *   <li>Dates and times must appear exactly in the tool results.
 * </ul>
 */
@Component
public class NumberValidator {

  /** Names written by the store: never facts, and removed from the answer before checking. */
  private static final Set<String> NAME_FIELDS =
      Set.of(
          "title", "productType", "term", "topTitles", "store", "domain", "weakestStage", "label");

  /** Other store-written values (links, size labels, store sentences): never facts. */
  private static final Set<String> IGNORED_FIELDS =
      Set.of(
          "productUrl",
          "imageUrl",
          "url",
          "handle",
          "text",
          "size",
          "soldOutSizes",
          "missingCoreSizes",
          "sizesAvailable",
          "sizesSoldOut",
          "coreRange",
          "currency",
          "source");

  private static final Pattern NUMBER =
      Pattern.compile("(?<![\\w.])[$€£]?(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?\\s?(%|k\\b)?");
  private static final Pattern DATE =
      Pattern.compile(
          "\\b\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}(?::\\d{2})?(?:\\.\\d+)?Z?)?|\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
  private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*\\d{1,2}[.)]\\s");
  private static final String SIZE_TOKEN =
      "(?:[A-Za-z]{1,2}\\s?)?\\d{1,3}(?:\\.\\d)?[A-Za-z]{0,2}|[0-9]?X{0,3}[SML]|XL|XXL";
  private static final Pattern SIZE_LIST =
      Pattern.compile(
          "\\bsizes?\\s+(?:"
              + SIZE_TOKEN
              + ")(?:\\s*(?:,|–|-|/|and|to|or)\\s*(?:"
              + SIZE_TOKEN
              + "))*",
          Pattern.CASE_INSENSITIVE);

  /** Result of a check. */
  public record Validation(boolean ok, int verified, List<String> unverified) {}

  public Validation check(String answer, List<ToolResult> results) {
    Set<BigDecimal> facts = new HashSet<>();
    List<String> storeText = new ArrayList<>();
    StringBuilder rawJson = new StringBuilder();
    for (ToolResult r : results) {
      collect(r.data(), null, facts, storeText);
      rawJson.append(r.data().toString());
    }
    String cleaned = answer;
    storeText.sort(Comparator.comparingInt(String::length).reversed());
    for (String text : storeText) {
      if (text.length() >= 3) {
        cleaned =
            Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)
                .matcher(cleaned)
                .replaceAll(" ");
      }
    }
    cleaned = SIZE_LIST.matcher(cleaned).replaceAll(" ");
    cleaned = LIST_MARKER.matcher(cleaned).replaceAll(" ");

    int verified = 0;
    List<String> unverified = new ArrayList<>();
    Matcher dates = DATE.matcher(cleaned);
    while (dates.find()) {
      if (rawJson.indexOf(dates.group()) >= 0) {
        verified++;
      } else {
        unverified.add(dates.group());
      }
    }
    cleaned = DATE.matcher(cleaned).replaceAll(" ");
    Matcher m = NUMBER.matcher(cleaned);
    while (m.find()) {
      String integer = m.group(1).replace(",", "");
      String fraction = m.group(2) == null ? "" : m.group(2);
      BigDecimal value = new BigDecimal(integer + fraction);
      int decimals = fraction.isEmpty() ? 0 : fraction.length() - 1;
      if ("k".equalsIgnoreCase(m.group(3))) {
        value = value.multiply(BigDecimal.valueOf(1000));
        decimals = Math.max(0, decimals - 3);
      }
      if (matches(value, decimals, facts)) {
        verified++;
      } else {
        unverified.add(m.group().trim());
      }
    }
    return new Validation(unverified.isEmpty(), verified, unverified);
  }

  private static boolean matches(BigDecimal value, int decimals, Set<BigDecimal> facts) {
    BigDecimal tolerance = BigDecimal.ONE.movePointLeft(decimals);
    if (value.signum() == 0) {
      return true; // "0 products" is never a made-up figure worth blocking an answer for
    }
    for (BigDecimal fact : facts) {
      if (value.subtract(fact).abs().compareTo(tolerance) <= 0) {
        return true;
      }
    }
    return false;
  }

  private static void collect(
      JsonNode node, String field, Set<BigDecimal> facts, List<String> storeText) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isNumber()) {
      facts.add(node.decimalValue());
      return;
    }
    if (node.isTextual()) {
      String text = node.asText();
      if (field != null && NAME_FIELDS.contains(field)) {
        storeText.add(text);
        return;
      }
      if (field != null && IGNORED_FIELDS.contains(field)) {
        return;
      }
      Matcher m = NUMBER.matcher(text);
      while (m.find()) {
        try {
          facts.add(
              new BigDecimal(m.group(1).replace(",", "") + (m.group(2) == null ? "" : m.group(2))));
        } catch (NumberFormatException e) {
          // not a number after all; ignore
        }
      }
      return;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        collect(child, field, facts, storeText);
      }
      return;
    }
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> e = fields.next();
      collect(e.getValue(), e.getKey(), facts, storeText);
    }
  }
}

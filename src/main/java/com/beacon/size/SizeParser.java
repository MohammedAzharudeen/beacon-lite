package com.beacon.size;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies size values as published by stores (formats verified on real stores). Anything not
 * matched is {@link SizeKind#UNKNOWN}, never guessed.
 */
public final class SizeParser {

  private static final String NUM = "(\\d{1,3}(?:\\.\\d{1,2})?)";
  private static final Pattern ONE_SIZE =
      Pattern.compile("^(one[ -]?size.*|onesz|os|o/s|1sfm|one size fits (all|most)|osfa|osfm)$");
  private static final Pattern NUMERIC = Pattern.compile("^(?:sz\\s*|size\\s*)?" + NUM + "$");
  private static final Pattern FRACTION = Pattern.compile("^(\\d{1,2})\\s*(?:1/2|½)$");
  private static final Pattern KIDS = Pattern.compile("^" + NUM + "\\s*([ckty])$");
  private static final Pattern WIDE = Pattern.compile("^" + NUM + "\\s*(?:w|wide)$");
  private static final Pattern DUAL =
      Pattern.compile("^(?:m|men'?s)\\s*" + NUM + "\\s*/\\s*(?:w|women'?s)\\s*" + NUM + "$");
  private static final Pattern NUMERIC_PAIR =
      Pattern.compile("^" + NUM + "\\s*[/-]\\s*" + NUM + "$");
  private static final Pattern LETTER_PAIR =
      Pattern.compile("^([a-z0-9]+)\\s*[/-]\\s*([a-z0-9]+)$");
  private static final Pattern VOLUME =
      Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:fl\\.?\\s*oz|oz|ml|l|g|kg|lb|lbs)\\b");
  private static final Pattern SET =
      Pattern.compile("\\b\\d+\\s*(?:piece|pc|pcs|pk|pack)\\b|\\bset\\b");
  private static final Pattern BEDDING =
      Pattern.compile(
          "^(twin( xl)?|full|queen|king|cal(ifornia)? king|full/queen|king/cal king|twin/twin xl)$");

  /** Letter ladder; 2XL = XXL, 2XS = XXS. Store abbreviations (XSMA, MED, XLRG…) are mapped. */
  private static final Map<String, Integer> LETTERS =
      Map.ofEntries(
          Map.entry("XXXS", 0),
          Map.entry("3XS", 0),
          Map.entry("XXS", 1),
          Map.entry("2XS", 1),
          Map.entry("2XSMA", 1),
          Map.entry("XS", 2),
          Map.entry("XSMA", 2),
          Map.entry("XSMALL", 2),
          Map.entry("XSM", 2),
          Map.entry("S", 3),
          Map.entry("SM", 3),
          Map.entry("SMALL", 3),
          Map.entry("M", 4),
          Map.entry("MED", 4),
          Map.entry("MEDIUM", 4),
          Map.entry("L", 5),
          Map.entry("LG", 5),
          Map.entry("LRG", 5),
          Map.entry("LARGE", 5),
          Map.entry("XL", 6),
          Map.entry("XLRG", 6),
          Map.entry("XLARGE", 6),
          Map.entry("XLG", 6),
          Map.entry("XXL", 7),
          Map.entry("2XL", 7),
          Map.entry("2XLRG", 7),
          Map.entry("XXLARGE", 7),
          Map.entry("XXXL", 8),
          Map.entry("3XL", 8),
          Map.entry("3XLRG", 8),
          Map.entry("4XL", 9),
          Map.entry("5XL", 10),
          Map.entry("6XL", 11),
          Map.entry("0X", 20),
          Map.entry("1X", 21),
          Map.entry("2X", 22),
          Map.entry("3X", 23),
          Map.entry("4X", 24),
          Map.entry("5X", 25));

  private SizeParser() {}

  public static ParsedSize parse(String raw) {
    String label = raw == null ? "" : raw.trim();
    String v = label.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    if (v.isEmpty()) {
      return new ParsedSize(label, SizeKind.UNKNOWN, "UNKNOWN", 0);
    }
    if (ONE_SIZE.matcher(v).matches()) {
      return new ParsedSize(label, SizeKind.ONE_SIZE, "ONE_SIZE", 0);
    }
    Matcher m;
    if ((m = NUMERIC.matcher(v)).matches()) {
      return new ParsedSize(label, SizeKind.NUMERIC, "NUMERIC", Double.parseDouble(m.group(1)));
    }
    if ((m = FRACTION.matcher(v)).matches()) {
      return new ParsedSize(label, SizeKind.NUMERIC, "NUMERIC", Integer.parseInt(m.group(1)) + 0.5);
    }
    if ((m = KIDS.matcher(v)).matches()) {
      // C (child), K (kids), T (toddler), Y (youth) are separate runs from adult sizes
      return new ParsedSize(
          label,
          SizeKind.NUMERIC,
          "NUMERIC_" + m.group(2).toUpperCase(Locale.ROOT),
          Double.parseDouble(m.group(1)));
    }
    if ((m = WIDE.matcher(v)).matches()) {
      return new ParsedSize(label, SizeKind.WIDE, "WIDE", Double.parseDouble(m.group(1)));
    }
    if ((m = DUAL.matcher(v)).matches()) {
      return new ParsedSize(label, SizeKind.DUAL, "DUAL", Double.parseDouble(m.group(1)));
    }
    Integer letter = letterRank(v);
    if (letter != null) {
      return new ParsedSize(label, SizeKind.LETTER, "LETTER", letter);
    }
    if ((m = NUMERIC_PAIR.matcher(v)).matches()) {
      return new ParsedSize(label, SizeKind.COMBO, "COMBO_NUMERIC", Double.parseDouble(m.group(1)));
    }
    if ((m = LETTER_PAIR.matcher(v)).matches()) {
      Integer first = letterRank(m.group(1));
      Integer second = letterRank(m.group(2));
      if (first != null && second != null) {
        return new ParsedSize(label, SizeKind.COMBO, "COMBO_LETTER", first);
      }
    }
    if (BEDDING.matcher(v).matches()) {
      return new ParsedSize(label, SizeKind.BEDDING, "BEDDING", 0);
    }
    if (VOLUME.matcher(v).find()) {
      return new ParsedSize(label, SizeKind.VOLUME, "VOLUME", 0);
    }
    if (SET.matcher(v).find()) {
      return new ParsedSize(label, SizeKind.SET, "SET", 0);
    }
    return new ParsedSize(label, SizeKind.UNKNOWN, "UNKNOWN", 0);
  }

  private static Integer letterRank(String lower) {
    String key = lower.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
    return LETTERS.get(key);
  }

  /** Positions (1-based) of options whose name contains "size", case-insensitive. */
  public static List<Integer> sizeOptionPositions(List<String> optionNames) {
    List<Integer> out = new ArrayList<>();
    for (int i = 0; i < optionNames.size(); i++) {
      if (optionNames.get(i) != null
          && optionNames.get(i).toLowerCase(Locale.ROOT).contains("size")) {
        out.add(i + 1);
      }
    }
    return out;
  }
}

package com.beacon.size;

import com.beacon.adapter.model.OptionData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds a product's size option, sorts its sizes (feed order is not size order) and marks the core
 * sizes: the middle {@code percent} of each fit-size run.
 */
public final class SizeAnalyzer {

  private static final List<SizeKind> RUN_ORDER =
      List.of(
          SizeKind.LETTER,
          SizeKind.COMBO,
          SizeKind.NUMERIC,
          SizeKind.WIDE,
          SizeKind.DUAL,
          SizeKind.ONE_SIZE,
          SizeKind.VOLUME,
          SizeKind.SET,
          SizeKind.BEDDING,
          SizeKind.MULTI);

  private final int corePercent;

  public SizeAnalyzer(int corePercent) {
    this.corePercent = corePercent;
  }

  public SizeRun analyse(ProductData product) {
    List<Integer> positions =
        SizeParser.sizeOptionPositions(product.options().stream().map(OptionData::name).toList());
    if (positions.isEmpty()) {
      return SizeRun.noSizes();
    }
    if (positions.size() > 1) {
      return multiDimension(product, positions);
    }
    int position = positions.get(0);
    Set<String> labels = new LinkedHashSet<>();
    for (VariantData v : product.variants()) {
      String label = v.option(position);
      if (label != null && !label.isBlank()) {
        labels.add(label.trim());
      }
    }
    if (labels.isEmpty()) {
      return SizeRun.noSizes();
    }
    List<ParsedSize> parsed = labels.stream().map(SizeParser::parse).toList();
    if (parsed.stream().anyMatch(p -> p.kind() == SizeKind.UNKNOWN)) {
      return SizeRun.unrecognised();
    }
    // Each run (e.g. regular vs wide, adult vs kids) is sorted and gets its own core range
    Map<String, List<ParsedSize>> runs = new LinkedHashMap<>();
    parsed.stream()
        .sorted(
            Comparator.comparingInt((ParsedSize p) -> RUN_ORDER.indexOf(p.kind()))
                .thenComparing(ParsedSize::runKey)
                .thenComparingDouble(ParsedSize::sortKey))
        .forEach(p -> runs.computeIfAbsent(p.runKey(), k -> new ArrayList<>()).add(p));
    List<SizeEntry> entries = new ArrayList<>();
    boolean weighted = false;
    for (List<ParsedSize> run : runs.values()) {
      boolean fit = run.get(0).kind().isFitSize();
      weighted |= fit;
      int n = run.size();
      int trim = (int) Math.floor(n * (100 - corePercent) / 200.0);
      for (int i = 0; i < n; i++) {
        boolean core = fit && i >= trim && i < n - trim;
        entries.add(new SizeEntry(run.get(i).label(), run.get(i).kind(), entries.size(), core));
      }
    }
    return new SizeRun(SizeRun.Status.ANALYSED, entries, weighted);
  }

  /** With two or more size options, each combination is one size, with no core weighting. */
  private static SizeRun multiDimension(ProductData product, List<Integer> positions) {
    Set<String> labels = new LinkedHashSet<>();
    for (VariantData v : product.variants()) {
      labels.add(multiLabel(v, positions));
    }
    List<SizeEntry> entries = new ArrayList<>();
    for (String label : labels) {
      entries.add(new SizeEntry(label, SizeKind.MULTI, entries.size(), false));
    }
    return new SizeRun(SizeRun.Status.ANALYSED, entries, false);
  }

  /** The size label of a variant, as used in {@link SizeRun}; {@code null} when it has none. */
  public static String labelOf(ProductData product, VariantData variant) {
    List<Integer> positions =
        SizeParser.sizeOptionPositions(product.options().stream().map(OptionData::name).toList());
    if (positions.isEmpty()) {
      return null;
    }
    if (positions.size() > 1) {
      return multiLabel(variant, positions);
    }
    String label = variant.option(positions.get(0));
    return label == null || label.isBlank() ? null : label.trim();
  }

  private static String multiLabel(VariantData v, List<Integer> positions) {
    List<String> parts = new ArrayList<>();
    for (int p : positions) {
      String value = v.option(p);
      parts.add(value == null ? "?" : value.trim());
    }
    return String.join(" / ", parts);
  }
}

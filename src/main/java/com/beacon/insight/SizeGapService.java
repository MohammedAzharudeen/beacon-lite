package com.beacon.insight;

import com.beacon.insight.report.SizeCell;
import com.beacon.insight.report.SizeGapRow;
import com.beacon.insight.report.SizeSummary;
import com.beacon.size.SizeEntry;
import com.beacon.size.SizeRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Size gaps and the size totals behind the headline. "Missing core sizes" = a core size sold out
 * while at least one other size is still available (a gap, not a discontinued style).
 */
public final class SizeGapService {

  private static final int MIN_SIZES_FOR_ONE_LEFT = 3;

  public List<SizeGapRow> gaps(StoreView store) {
    List<SizeGapRow> rows = new ArrayList<>();
    for (ProductView view : analysable(store)) {
      Map<String, Boolean> availability = view.sizeAvailability();
      long soldOut = availability.values().stream().filter(a -> !a).count();
      if (soldOut == 0) {
        continue;
      }
      List<SizeEntry> sizes = view.sizes().sizes();
      List<SizeCell> cells =
          sizes.stream()
              .map(s -> new SizeCell(s.label(), availability.get(s.label()), s.core()))
              .toList();
      int missingCore =
          (int) sizes.stream().filter(s -> s.core() && !availability.get(s.label())).count();
      int available = sizes.size() - (int) soldOut;
      rows.add(
          new SizeGapRow(
              view.product().externalId(),
              view.product().title(),
              view.product().productType(),
              view.product().imageUrls().isEmpty() ? null : view.product().imageUrls().get(0),
              view.productUrl(),
              cells,
              coreRange(sizes),
              missingCore,
              (int) soldOut,
              sizes.size(),
              available == 1 && sizes.size() >= MIN_SIZES_FOR_ONE_LEFT));
    }
    rows.sort(
        Comparator.comparingInt(SizeGapRow::missingCore)
            .reversed()
            .thenComparing(Comparator.comparingInt(SizeGapRow::soldOutSizes).reversed())
            .thenComparing(SizeGapRow::title));
    return rows;
  }

  public SizeSummary summary(StoreView store) {
    int analysed = 0;
    int missingCore = 0;
    int oneLeft = 0;
    int fullySoldOut = 0;
    int noSize = 0;
    int unrecognised = 0;
    for (ProductView view : store.products()) {
      if (view.excluded()) {
        continue;
      }
      switch (view.sizes().status()) {
        case NO_SIZE_OPTION -> noSize++;
        case UNRECOGNISED -> unrecognised++;
        case ANALYSED -> {
          if (view.sizes().hasOnlyOneSize()) {
            continue;
          }
          analysed++;
          Map<String, Boolean> availability = view.sizeAvailability();
          long available = availability.values().stream().filter(a -> a).count();
          if (available == 0) {
            fullySoldOut++;
            continue;
          }
          boolean coreGap =
              view.sizes().sizes().stream().anyMatch(s -> s.core() && !availability.get(s.label()));
          if (coreGap) {
            missingCore++;
          }
          if (available == 1 && availability.size() >= MIN_SIZES_FOR_ONE_LEFT) {
            oneLeft++;
          }
        }
      }
    }
    return new SizeSummary(analysed, missingCore, oneLeft, fullySoldOut, noSize, unrecognised);
  }

  private static List<ProductView> analysable(StoreView store) {
    return store.products().stream()
        .filter(v -> !v.excluded())
        .filter(v -> v.sizes().status() == SizeRun.Status.ANALYSED && !v.sizes().hasOnlyOneSize())
        .toList();
  }

  /** Core sizes as ranges, one per size run, e.g. "7–9.5, 7.0W–9.0W". */
  static String coreRange(List<SizeEntry> sizes) {
    List<String> ranges = new ArrayList<>();
    SizeEntry start = null;
    SizeEntry previous = null;
    for (SizeEntry s : sizes) {
      boolean continues =
          previous != null && s.core() && previous.core() && s.kind() == previous.kind();
      if (s.core() && !continues) {
        if (start != null) {
          ranges.add(range(start, previous));
        }
        start = s;
      } else if (!s.core() && start != null) {
        ranges.add(range(start, previous));
        start = null;
      }
      previous = s;
    }
    if (start != null) {
      ranges.add(range(start, previous));
    }
    return ranges.isEmpty() ? null : String.join(", ", ranges);
  }

  private static String range(SizeEntry first, SizeEntry last) {
    return first.label().equals(last.label()) ? first.label() : first.label() + "–" + last.label();
  }
}

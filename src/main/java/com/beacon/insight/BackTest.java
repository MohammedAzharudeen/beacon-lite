package com.beacon.insight;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.config.Assumptions;
import com.beacon.insight.report.ChangeCounts;
import com.beacon.insight.report.InsightReport;
import com.beacon.insight.report.RestockRow;
import com.beacon.size.SizeAnalyzer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accuracy back-test of "selling fast" flags, run over a store's snapshots in time order. A flag is
 * a restock candidate with at least one core size sold out and the sell-out-speed signal. It is a
 * hit when, within the next 24 hours of snapshots, the product loses at least one more size or
 * sells out completely.
 */
public final class BackTest {

  private static final Duration HORIZON = Duration.ofHours(24);

  private final Assumptions assumptions;

  public BackTest(Assumptions assumptions) {
    this.assumptions = assumptions;
  }

  /**
   * @param flags flags raised
   * @param evaluated flags with at least one later snapshot inside the horizon
   * @param hits evaluated flags that were confirmed
   */
  public record Result(int snapshots, int flags, int evaluated, int hits) {

    /** Hit rate over evaluated flags, or {@code null} when nothing could be evaluated. */
    public Double hitRate() {
      return evaluated == 0 ? null : (double) hits / evaluated;
    }
  }

  /**
   * @param snapshots one store's snapshots, oldest first
   */
  public Result run(List<CatalogSnapshotData> snapshots) {
    int flags = 0;
    int evaluated = 0;
    int hits = 0;
    for (int i = 1; i < snapshots.size(); i++) {
      CatalogSnapshotData previous = snapshots.get(i - 1);
      CatalogSnapshotData current = snapshots.get(i);
      InsightReport report =
          new ReportBuilder(assumptions)
              .build(0, i, current, changes(previous, current), Map.of(), current.capturedAt());
      for (RestockRow row : report.restock()) {
        boolean coreSoldOut = row.sizes().stream().anyMatch(c -> c.core() && !c.available());
        if (!coreSoldOut || !row.signals().contains(DemandSignal.SELL_OUT_SPEED)) {
          continue;
        }
        flags++;
        List<CatalogSnapshotData> later = within(snapshots, i, current.capturedAt().plus(HORIZON));
        if (later.isEmpty()) {
          continue;
        }
        evaluated++;
        int soldOutNow = row.soldOutSizes();
        if (later.stream().anyMatch(s -> worse(s, row.productId(), soldOutNow))) {
          hits++;
        }
      }
    }
    return new Result(snapshots.size(), flags, evaluated, hits);
  }

  /** Variants that went from available to sold out between two snapshots. */
  static ChangeContext changes(CatalogSnapshotData previous, CatalogSnapshotData current) {
    Map<Long, Boolean> before = new HashMap<>();
    previous
        .products()
        .forEach(p -> p.variants().forEach(v -> before.put(v.externalId(), v.available())));
    Set<Long> soldOut = new HashSet<>();
    int restocked = 0;
    int priceChanges = 0;
    Map<Long, VariantData> beforeVariants = new HashMap<>();
    previous
        .products()
        .forEach(p -> p.variants().forEach(v -> beforeVariants.put(v.externalId(), v)));
    for (ProductData p : current.products()) {
      for (VariantData v : p.variants()) {
        VariantData old = beforeVariants.get(v.externalId());
        if (old == null) {
          continue;
        }
        if (old.available() && !v.available()) {
          soldOut.add(v.externalId());
        } else if (!old.available() && v.available()) {
          restocked++;
        }
        if (old.price().compareTo(v.price()) != 0) {
          priceChanges++;
        }
      }
    }
    return new ChangeContext(new ChangeCounts(soldOut.size(), restocked, priceChanges), soldOut);
  }

  private static List<CatalogSnapshotData> within(
      List<CatalogSnapshotData> all, int from, Instant until) {
    List<CatalogSnapshotData> out = new ArrayList<>();
    for (int j = from + 1; j < all.size(); j++) {
      if (all.get(j).capturedAt().isAfter(until)) {
        break;
      }
      out.add(all.get(j));
    }
    return out;
  }

  /** True when the product has more sold-out sizes than before, or is gone fully sold out. */
  private static boolean worse(CatalogSnapshotData later, long productId, int soldOutBefore) {
    for (ProductData p : later.products()) {
      if (p.externalId() != productId) {
        continue;
      }
      if (p.fullySoldOut()) {
        return true;
      }
      Map<String, Boolean> available = new HashMap<>();
      for (VariantData v : p.variants()) {
        String label = SizeAnalyzer.labelOf(p, v);
        if (label != null) {
          available.merge(label, v.available(), Boolean::logicalOr);
        }
      }
      long soldOut = available.values().stream().filter(a -> !a).count();
      return soldOut > soldOutBefore;
    }
    return false;
  }
}

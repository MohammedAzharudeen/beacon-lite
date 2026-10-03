package com.beacon.insight;

import com.beacon.adapter.model.VariantData;
import com.beacon.common.Money;
import com.beacon.config.Assumptions;
import com.beacon.insight.report.EstimatedMoney;
import com.beacon.insight.report.RestockRow;
import com.beacon.insight.report.SizeCell;
import com.beacon.intent.IntentSignalProvider;
import com.beacon.intent.PublicStandInProvider;
import com.beacon.size.SizeAnalyzer;
import com.beacon.size.SizeEntry;
import com.beacon.size.SizeRun;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ranks restock candidates by Demand × Gap × Value. Zero stock alone is not a signal: a product
 * needs at least one demand signal to be listed. $ at risk is an estimate.
 */
public final class RestockService {

  private final Assumptions assumptions;
  private final IntentSignalProvider signals;
  private final DemandModel demandModel;

  public RestockService(Assumptions assumptions, IntentSignalProvider signals) {
    this.assumptions = assumptions;
    this.signals = signals;
    this.demandModel = new DemandModel(assumptions.demand());
  }

  public List<RestockRow> rank(StoreView store) {
    String currency = store.data().store().currency();
    List<RestockRow> rows = new ArrayList<>();
    for (ProductView view : store.products()) {
      if (view.excluded()
          || view.sizes().status() != SizeRun.Status.ANALYSED
          || view.sizes().hasOnlyOneSize()) {
        continue;
      }
      double gap = gap(view);
      // A size must be fully sold out (in every colour) to be a restock candidate
      if (gap <= 0 || !view.sizeAvailability().containsValue(false)) {
        continue;
      }
      Set<DemandSignal> present = signals.signals(view, store);
      if (present.isEmpty()) {
        continue;
      }
      double demand = demandModel.score(present);
      BigDecimal price = view.price();
      BigDecimal atRisk =
          price
              .multiply(BigDecimal.valueOf(demand))
              .multiply(BigDecimal.valueOf(assumptions.demand().weeklyDemandBaselineUnits()))
              .multiply(BigDecimal.valueOf(gap))
              .setScale(2, RoundingMode.HALF_UP);
      Map<String, Boolean> availability = view.sizeAvailability();
      List<SizeCell> cells =
          view.sizes().sizes().stream()
              .map(
                  s ->
                      new SizeCell(
                          s.label(), availability.getOrDefault(s.label(), false), s.core()))
              .toList();
      List<DemandSignal> ordered = present.stream().sorted().toList();
      rows.add(
          new RestockRow(
              view.product().externalId(),
              view.product().title(),
              view.product().productType(),
              view.product().imageUrls().isEmpty() ? null : view.product().imageUrls().get(0),
              view.productUrl(),
              cells,
              (int) cells.stream().filter(c -> !c.available()).count(),
              cells.size(),
              new Money(price, currency),
              ordered,
              round3(demand),
              round3(gap),
              EstimatedMoney.of(new Money(atRisk, currency)),
              demandModel.confidence(present),
              ordered.stream()
                  .map(PublicStandInProvider.SWYM_SIGNAL::get)
                  .collect(Collectors.joining("; "))));
    }
    rows.sort(
        Comparator.comparing((RestockRow r) -> new BigDecimal(r.atRiskPerWeek().amount()))
            .reversed()
            .thenComparing(Comparator.comparingDouble(RestockRow::gapScore).reversed())
            .thenComparing(RestockRow::title));
    return rows;
  }

  /**
   * Size-weighted share of variants sold out: core sizes weigh {@code gapWeights.core}, edge sizes
   * {@code gapWeights.edge}; unweighted kinds (volume, sets, multi-dimension) count equally.
   */
  double gap(ProductView view) {
    Map<String, SizeEntry> byLabel = view.sizes().byLabel();
    double total = 0;
    double soldOut = 0;
    for (VariantData v : view.product().variants()) {
      SizeEntry entry = byLabel.get(SizeAnalyzer.labelOf(view.product(), v));
      if (entry == null) {
        continue;
      }
      double weight =
          view.sizes().weighted()
              ? (entry.core() ? assumptions.gapWeights().core() : assumptions.gapWeights().edge())
              : 1.0;
      total += weight;
      if (!v.available()) {
        soldOut += weight;
      }
    }
    return total == 0 ? 0 : soldOut / total;
  }

  private static double round3(double value) {
    return Math.round(value * 1000) / 1000.0;
  }
}

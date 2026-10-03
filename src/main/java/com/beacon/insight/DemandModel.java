package com.beacon.insight;

import com.beacon.config.Assumptions;
import java.util.Set;

/** Turns demand signals into a 0–1 score and a confidence label. */
public final class DemandModel {

  private final Assumptions.Demand config;

  public DemandModel(Assumptions.Demand config) {
    this.config = config;
  }

  /** Sum of the weights of the signals present (weights add up to 1). */
  public double score(Set<DemandSignal> signals) {
    Assumptions.SignalWeights w = config.signalWeights();
    double score = 0;
    for (DemandSignal s : signals) {
      score +=
          switch (s) {
            case BEST_SELLER_COLLECTION -> w.bestSellerCollection();
            case PROMOTED -> w.promoted();
            case SELL_OUT_SPEED -> w.sellOutSpeed();
            case FULL_PRICE -> w.fullPrice();
            case RECENTLY_LAUNCHED -> w.recentlyLaunched();
          };
    }
    return Math.min(1.0, score);
  }

  /** HIGH with ≥ high signals, MEDIUM with ≥ medium, otherwise LOW. */
  public Confidence confidence(Set<DemandSignal> signals) {
    if (signals.size() >= config.confidence().high()) {
      return Confidence.HIGH;
    }
    if (signals.size() >= config.confidence().medium()) {
      return Confidence.MEDIUM;
    }
    return Confidence.LOW;
  }
}

package com.beacon.insight.report;

import com.beacon.common.Money;
import com.beacon.insight.Confidence;
import com.beacon.insight.DemandSignal;
import java.util.List;

/**
 * One restock candidate. Demand × Gap × Value; the $ figure is an estimate.
 *
 * @param productId the store's product id
 * @param signals demand signals present for this product
 * @param swymSignalLabel which Swym intent signal each public stand-in would become
 */
public record RestockRow(
    long productId,
    String title,
    String productType,
    String imageUrl,
    String productUrl,
    List<SizeCell> sizes,
    int soldOutSizes,
    int totalSizes,
    Money price,
    List<DemandSignal> signals,
    double demandScore,
    double gapScore,
    EstimatedMoney atRiskPerWeek,
    Confidence confidence,
    String swymSignalLabel) {

  public RestockRow {
    sizes = List.copyOf(sizes);
    signals = List.copyOf(signals);
  }
}

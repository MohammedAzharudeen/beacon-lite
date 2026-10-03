package com.beacon.insight;

import com.beacon.config.Assumptions;
import com.beacon.insight.report.ProductRef;
import com.beacon.insight.report.PromotedSoldOuts;
import java.util.List;

/**
 * Fully sold-out products the store still promotes: linked from the home page, in a best-seller
 * collection, or in a collection linked from the home page.
 */
public final class PromotedService {

  private final Assumptions.Signals config;

  public PromotedService(Assumptions.Signals config) {
    this.config = config;
  }

  public PromotedSoldOuts find(StoreView store) {
    List<ProductRef> soldOut =
        store.products().stream()
            .filter(v -> v.promoted() && !v.excluded() && v.product().fullySoldOut())
            .map(ReportRefs::ref)
            .toList();
    int promoted = (int) store.products().stream().filter(ProductView::promoted).count();
    int homeLinks =
        store.data().signals() == null ? 0 : store.data().signals().home().productHandles().size();
    String notice =
        homeLinks < config.minHomeLinks()
            ? "Home page links mostly load via JavaScript ("
                + homeLinks
                + " product links in the page HTML): checked collections only"
            : null;
    return new PromotedSoldOuts(soldOut, promoted, notice);
  }
}

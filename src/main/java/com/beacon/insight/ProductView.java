package com.beacon.insight;

import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import com.beacon.size.SizeAnalyzer;
import com.beacon.size.SizeEntry;
import com.beacon.size.SizeRun;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A product with everything the insight rules need: its sizes, exclusion and promotion status.
 *
 * @param productUrl link to the live product page
 */
public record ProductView(
    ProductData product,
    SizeRun sizes,
    Optional<ExclusionReason> exclusion,
    boolean inBestSellerCollection,
    boolean promoted,
    String productUrl) {

  public boolean excluded() {
    return exclusion.isPresent();
  }

  /** Per size label: true when any variant in that size is available. Sorted by size. */
  public Map<String, Boolean> sizeAvailability() {
    Map<String, Boolean> byLabel = new LinkedHashMap<>();
    for (SizeEntry entry : sizes.sizes()) {
      byLabel.put(entry.label(), false);
    }
    for (VariantData v : product.variants()) {
      String label = SizeAnalyzer.labelOf(product, v);
      if (label != null && byLabel.containsKey(label) && v.available()) {
        byLabel.put(label, true);
      }
    }
    return byLabel;
  }

  public List<SizeEntry> soldOutSizes() {
    Map<String, Boolean> availability = sizeAvailability();
    List<SizeEntry> out = new ArrayList<>();
    for (SizeEntry entry : sizes.sizes()) {
      if (!availability.getOrDefault(entry.label(), false)) {
        out.add(entry);
      }
    }
    return out;
  }

  /** The lowest current variant price (conservative for $ estimates). */
  public BigDecimal price() {
    return product.variants().stream()
        .map(VariantData::price)
        .min(Comparator.naturalOrder())
        .orElse(BigDecimal.ZERO);
  }
}

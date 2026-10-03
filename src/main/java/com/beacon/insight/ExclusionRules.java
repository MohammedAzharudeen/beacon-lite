package com.beacon.insight;

import com.beacon.adapter.model.ProductData;
import com.beacon.config.Assumptions;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which products are kept out of restock logic. Patterns come from {@code assumptions.yml}
 * and match case-insensitively as substrings.
 */
public final class ExclusionRules {

  private final Assumptions.Exclusions config;

  public ExclusionRules(Assumptions.Exclusions config) {
    this.config = config;
  }

  /**
   * The tag- and type-based reason, if any. "Likely discontinued" needs collection data, see {@link
   * #exclusion(ProductData, Set)}.
   */
  public Optional<ExclusionReason> basicExclusion(ProductData product) {
    if (anyTagMatches(product.tags(), config.preOrderTags())) {
      return Optional.of(ExclusionReason.PRE_ORDER);
    }
    if (anyTagMatches(product.tags(), config.backOrderTags())) {
      return Optional.of(ExclusionReason.BACK_ORDER);
    }
    if (typeMatches(product, config.bundleTypes())) {
      return Optional.of(ExclusionReason.BUNDLE);
    }
    if (typeOrHandleMatches(product, config.nonPhysicalTypes())) {
      return Optional.of(ExclusionReason.NON_PHYSICAL);
    }
    return Optional.empty();
  }

  /**
   * Full rule including "likely discontinued": every variant sold out, not in a best-seller
   * collection, and was discounted (any variant with compare-at above price).
   *
   * @param bestSellerIds external ids of products in the store's best-seller collections
   */
  public Optional<ExclusionReason> exclusion(ProductData product, Set<Long> bestSellerIds) {
    Optional<ExclusionReason> basic = basicExclusion(product);
    if (basic.isPresent()) {
      return basic;
    }
    Assumptions.LikelyDiscontinued rule = config.likelyDiscontinued();
    boolean soldOut = !rule.fullySoldOut() || product.fullySoldOut();
    boolean notBestSeller =
        !rule.notInBestSellerCollection() || !bestSellerIds.contains(product.externalId());
    boolean discounted = !rule.wasDiscounted() || wasDiscounted(product);
    if (product.fullySoldOut() && soldOut && notBestSeller && discounted) {
      return Optional.of(ExclusionReason.LIKELY_DISCONTINUED);
    }
    return Optional.empty();
  }

  static boolean wasDiscounted(ProductData product) {
    return product.variants().stream()
        .anyMatch(v -> v.compareAtPrice() != null && v.compareAtPrice().compareTo(v.price()) > 0);
  }

  private static boolean anyTagMatches(List<String> tags, List<String> patterns) {
    for (String tag : tags) {
      String t = tag.toLowerCase(Locale.ROOT);
      for (String p : patterns) {
        if (t.contains(p.toLowerCase(Locale.ROOT))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean typeMatches(ProductData product, List<String> patterns) {
    String type =
        product.productType() == null ? "" : product.productType().toLowerCase(Locale.ROOT);
    return patterns.stream().anyMatch(p -> type.contains(p.toLowerCase(Locale.ROOT)));
  }

  private static boolean typeOrHandleMatches(ProductData product, List<String> patterns) {
    String type =
        product.productType() == null ? "" : product.productType().toLowerCase(Locale.ROOT);
    String handle = product.handle() == null ? "" : product.handle().toLowerCase(Locale.ROOT);
    for (String p : patterns) {
      String pattern = p.toLowerCase(Locale.ROOT);
      if (type.contains(pattern) || handle.contains(pattern.replace(' ', '-'))) {
        return true;
      }
    }
    return false;
  }
}

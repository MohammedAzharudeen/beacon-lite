package com.beacon.insight;

import com.beacon.adapter.model.AltTextSample;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.SourceStatus;
import com.beacon.config.Assumptions;
import com.beacon.insight.report.CatalogQuality;
import com.beacon.insight.report.ProductRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Catalog hygiene. Never claims "all products are missing alt text": the catalog feed has no alt
 * field, so alt text is sampled from product JSON.
 */
public final class CatalogQualityService {

  private static final Pattern SPREADSHEET_ERROR =
      Pattern.compile("#(REF!|N/A|VALUE!|NAME\\?|DIV/0!|NULL!)");
  private static final int EXAMPLES = 10;

  private final Assumptions.Catalog config;

  public CatalogQualityService(Assumptions.Catalog config) {
    this.config = config;
  }

  public CatalogQuality assess(StoreView store) {
    int fewImages = 0;
    List<ProductRef> examples = new ArrayList<>();
    int thinDescriptions = 0;
    int thinTitles = 0;
    int blankType = 0;
    int untagged = 0;
    Map<String, TreeSet<String>> typesByLower = new TreeMap<>();
    TreeSet<String> spreadsheetErrors = new TreeSet<>();
    for (ProductView view : store.products()) {
      ProductData p = view.product();
      if (p.imageUrls().size() < config.minImages()) {
        fewImages++;
        if (examples.size() < EXAMPLES) {
          examples.add(ReportRefs.ref(view));
        }
      }
      if (p.descriptionLength() < config.thinDescriptionChars()) {
        thinDescriptions++;
      }
      if (p.title() == null || p.title().strip().length() < config.thinTitleChars()) {
        thinTitles++;
      }
      String type = p.productType() == null ? "" : p.productType().strip();
      if (type.isEmpty()) {
        blankType++;
      } else {
        typesByLower.computeIfAbsent(type.toLowerCase(Locale.ROOT), k -> new TreeSet<>()).add(type);
      }
      if (p.tags().isEmpty()) {
        untagged++;
      }
      if (SPREADSHEET_ERROR.matcher(type).find()) {
        spreadsheetErrors.add(type);
      }
      if (p.title() != null && SPREADSHEET_ERROR.matcher(p.title()).find()) {
        spreadsheetErrors.add(p.title());
      }
    }
    List<List<String>> caseVariants =
        typesByLower.values().stream().filter(s -> s.size() > 1).map(s -> List.copyOf(s)).toList();
    return new CatalogQuality(
        fewImages,
        examples,
        config.minImages(),
        altText(store),
        thinDescriptions,
        thinTitles,
        blankType,
        untagged,
        caseVariants,
        List.copyOf(spreadsheetErrors));
  }

  private static CatalogQuality.AltTextResult altText(StoreView store) {
    AltTextSample sample = store.data().signals() == null ? null : store.data().signals().altText();
    if (sample == null || sample.status() != SourceStatus.FETCHED || sample.sampled() == 0) {
      String reason =
          sample != null && sample.status() == SourceStatus.BLOCKED_BY_ROBOTS
              ? "Not checked: product JSON is blocked by robots.txt"
              : "Not checked: product JSON not available on this store";
      return new CatalogQuality.AltTextResult(0, 0, reason);
    }
    return new CatalogQuality.AltTextResult(
        sample.sampled(),
        sample.productsWithAllAlt(),
        sample.productsWithAllAlt()
            + " of "
            + sample.sampled()
            + " sampled products have alt text on all images");
  }
}

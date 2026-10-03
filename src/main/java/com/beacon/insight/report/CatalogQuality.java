package com.beacon.insight.report;

import java.util.List;

/**
 * Catalog hygiene findings. Alt text is sampled from product JSON, since the catalog feed has no
 * alt field.
 *
 * @param altText e.g. "0 of 30 sampled products have alt text on all images", or {@code null}
 * @param caseVariantTypes product types that differ only by letter case, e.g. ["Tops", "TOPS"]
 * @param spreadsheetErrors product types or titles containing spreadsheet errors such as #REF!
 */
public record CatalogQuality(
    int fewImages,
    List<ProductRef> fewImagesExamples,
    int minImages,
    AltTextResult altText,
    int thinDescriptions,
    int thinTitles,
    int blankProductType,
    int untagged,
    List<List<String>> caseVariantTypes,
    List<String> spreadsheetErrors) {

  public CatalogQuality {
    fewImagesExamples = List.copyOf(fewImagesExamples);
    caseVariantTypes = List.copyOf(caseVariantTypes);
    spreadsheetErrors = List.copyOf(spreadsheetErrors);
  }

  /** Sampled alt-text result; {@code text} says "Not checked" with the reason when unavailable. */
  public record AltTextResult(int sampled, int productsWithAllAlt, String text) {}
}

package com.beacon.journey;

import com.beacon.adapter.model.HomePageSignals;
import com.beacon.adapter.model.PolicySignals;
import com.beacon.adapter.model.ProductPageSignals;
import com.beacon.adapter.model.SearchSignals;
import com.beacon.adapter.model.SourceStatus;
import com.beacon.adapter.model.StorefrontSignals;
import com.beacon.config.Assumptions;
import com.beacon.insight.ProductView;
import com.beacon.insight.StoreView;
import com.beacon.insight.report.CatalogQuality;
import com.beacon.insight.report.Kpis;
import com.beacon.insight.report.PricingInsights;
import com.beacon.insight.report.SizeSummary;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The six-stage journey scorecard. Each check reports a status; stage scores use only checks that
 * ran (equal weights) and show coverage; the overall score is the mean of stages.
 */
public final class JourneyService {

  private static final int GOOD_ENOUGH = 70;

  private final Assumptions assumptions;

  public JourneyService(Assumptions assumptions) {
    this.assumptions = assumptions;
  }

  /** Inputs computed by the insight rules that the scorecard reuses. */
  public record Inputs(
      Kpis kpis, SizeSummary sizes, CatalogQuality catalog, PricingInsights pricing) {}

  public List<JourneyStage> score(StoreView store, Inputs in) {
    StorefrontSignals s = store.data().signals();
    Set<String> apps = apps(s);
    return List.of(
        stage(
            JourneyStageKey.DISCOVER, "Discover", List.of(homeWeight(s.home()), scripts(s.home()))),
        stage(
            JourneyStageKey.BROWSE,
            "Browse",
            List.of(
                search(s.search()), findability(store, in.catalog()), soldOutBestSellers(store))),
        stage(
            JourneyStageKey.PRODUCT_PAGE,
            "Product page",
            List.of(
                imageCount(store, in.catalog()),
                altText(in.catalog()),
                descriptions(store, in.catalog()),
                appCheck(
                    "REVIEWS_APP",
                    "Reviews app",
                    apps,
                    List.of("REVIEWS"),
                    s.home(),
                    "Add product reviews; shoppers rely on them to choose size and fit"),
                sizeGuide(s.productPages()))),
        stage(
            JourneyStageKey.SIZE_STOCK,
            "Size & stock",
            List.of(variantsSoldOut(in.kpis()), coreGaps(in.sizes()))),
        stage(
            JourneyStageKey.CART_CHECKOUT,
            "Cart & checkout",
            List.of(
                freeShipping(in.pricing()),
                appCheck(
                    "BNPL",
                    "Buy now, pay later",
                    apps,
                    assumptions.detection().bnplApps(),
                    s.home(),
                    "Offer buy now, pay later (e.g. Afterpay or Klarna) for higher-priced items"),
                appCheck(
                    "SHOP_PAY",
                    "Shop Pay",
                    apps,
                    List.of("SHOP_PAY"),
                    s.home(),
                    "Enable Shop Pay for faster checkout"),
                returns(s.returns()))),
        stage(
            JourneyStageKey.COME_BACK,
            "Come back",
            List.of(
                appCheck(
                    "WISHLIST_APP",
                    "Wishlist",
                    apps,
                    assumptions.detection().wishlistApps(),
                    s.home(),
                    "Add a wishlist so shoppers can save items and come back"),
                notifyMe(s.productPages(), apps),
                appCheck(
                    "EMAIL_CAPTURE",
                    "Email capture",
                    apps,
                    assumptions.detection().emailCaptureApps(),
                    s.home(),
                    "Capture emails so you can bring shoppers back"))));
  }

  /** Mean of stage scores that have a value, or {@code null} when no stage could be scored. */
  public static Integer overall(List<JourneyStage> stages) {
    List<Integer> scores = stages.stream().map(JourneyStage::score).filter(x -> x != null).toList();
    if (scores.isEmpty()) {
      return null;
    }
    return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
  }

  private static JourneyStage stage(JourneyStageKey key, String label, List<JourneyCheck> checks) {
    List<Integer> scores =
        checks.stream().filter(JourneyCheck::ran).map(JourneyCheck::score).toList();
    Integer score =
        scores.isEmpty()
            ? null
            : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    return new JourneyStage(key, label, score, scores.size(), checks.size(), checks);
  }

  // --- Discover ---

  private JourneyCheck homeWeight(HomePageSignals home) {
    if (home.status() != SourceStatus.FETCHED) {
      return notRun(
          "HOME_PAGE_WEIGHT", "Home page weight", home.status(), home.robotsRule(), "home page");
    }
    int score = assumptions.journeyThresholds().homePageBytes().score(home.bytes());
    return checked(
        "HOME_PAGE_WEIGHT",
        "Home page weight",
        score,
        "Home page HTML is " + mb(home.bytes()) + " (before images and scripts load)",
        "Reduce home page weight (fewer apps and scripts, lighter sections) so it loads faster on phones");
  }

  private JourneyCheck scripts(HomePageSignals home) {
    if (home.status() != SourceStatus.FETCHED) {
      return notRun(
          "SCRIPT_COUNT", "Scripts on home page", home.status(), home.robotsRule(), "home page");
    }
    int score = assumptions.journeyThresholds().homePageScripts().score(home.scriptCount());
    return checked(
        "SCRIPT_COUNT",
        "Scripts on home page",
        score,
        home.scriptCount() + " script tags in the home page HTML",
        "Remove unused apps and scripts; each one slows the first page shoppers see");
  }

  // --- Browse ---

  private static JourneyCheck search(SearchSignals search) {
    if (search.status() == SourceStatus.BLOCKED_BY_ROBOTS) {
      return new JourneyCheck(
          "SEARCH_TEST",
          "Search test",
          CheckStatus.NOT_CHECKED_ROBOTS,
          null,
          "Not checked: the store's robots.txt blocks search",
          null,
          "robots.txt: " + search.robotsRule());
    }
    if (search.probes().isEmpty()) {
      return new JourneyCheck(
          "SEARCH_TEST",
          "Search test",
          CheckStatus.NOT_AVAILABLE,
          null,
          "Search couldn't be tested on this store",
          null,
          "No search results available");
    }
    int good = 0;
    List<String> parts = new ArrayList<>();
    List<String> weak = new ArrayList<>();
    for (SearchSignals.SearchProbe p : search.probes()) {
      boolean ok = p.relevantCount() > 0;
      good += ok ? 1 : 0;
      parts.add(
          "\"" + p.term() + "\": " + p.relevantCount() + " of " + p.resultCount() + " relevant");
      if (!ok) {
        weak.add("\"" + p.term() + "\"");
      }
    }
    int score = (int) Math.round(100.0 * good / search.probes().size());
    return new JourneyCheck(
        "SEARCH_TEST",
        "Search test",
        CheckStatus.CHECKED,
        score,
        String.join("; ", parts),
        weak.isEmpty()
            ? null
            : "Fix search results for " + String.join(", ", weak) + " (zero or irrelevant results)",
        null);
  }

  private JourneyCheck findability(StoreView store, CatalogQuality catalog) {
    int total = store.products().size();
    if (total == 0) {
      return notAvailable("FINDABILITY", "Catalog findability", "No products read");
    }
    int ok = 0;
    for (ProductView v : store.products()) {
      String type = v.product().productType() == null ? "" : v.product().productType().strip();
      String title = v.product().title() == null ? "" : v.product().title().strip();
      if (!type.isEmpty()
          && title.length() >= assumptions.catalog().thinTitleChars()
          && !v.product().tags().isEmpty()) {
        ok++;
      }
    }
    int score = (int) Math.round(100.0 * ok / total);
    String evidence =
        catalog.blankProductType()
            + " products without a type, "
            + catalog.thinTitles()
            + " with short titles, "
            + catalog.untagged()
            + " untagged"
            + (catalog.caseVariantTypes().isEmpty()
                ? ""
                : "; types differing only by case: " + catalog.caseVariantTypes());
    return checked(
        "FINDABILITY",
        "Catalog findability",
        score,
        evidence,
        "Give every product a consistent type, a descriptive title and tags so filters and search find it");
  }

  private static JourneyCheck soldOutBestSellers(StoreView store) {
    if (!store.hasBestSellerCollection()) {
      return notAvailable(
          "SOLD_OUT_BEST_SELLERS",
          "Sold-out items in best sellers",
          "The store has no best-seller collection");
    }
    List<ProductView> best =
        store.products().stream().filter(ProductView::inBestSellerCollection).toList();
    if (best.isEmpty()) {
      return notAvailable(
          "SOLD_OUT_BEST_SELLERS",
          "Sold-out items in best sellers",
          "Best-seller collection is empty");
    }
    long soldOut = best.stream().filter(v -> v.product().fullySoldOut()).count();
    int score = (int) Math.round(100.0 * (best.size() - soldOut) / best.size());
    return checked(
        "SOLD_OUT_BEST_SELLERS",
        "Sold-out items in best sellers",
        score,
        soldOut + " of " + best.size() + " products in best-seller collections are fully sold out",
        "Restock or hide sold-out items in best-seller collections");
  }

  // --- Product page ---

  private JourneyCheck imageCount(StoreView store, CatalogQuality catalog) {
    int total = store.products().size();
    if (total == 0) {
      return notAvailable("IMAGE_COUNT", "Product images", "No products read");
    }
    int score = (int) Math.round(100.0 * (total - catalog.fewImages()) / total);
    return checked(
        "IMAGE_COUNT",
        "Product images",
        score,
        catalog.fewImages()
            + " of "
            + total
            + " products have fewer than "
            + catalog.minImages()
            + " images",
        "Add more photos (angles, on-model, detail) to products with few images");
  }

  private static JourneyCheck altText(CatalogQuality catalog) {
    CatalogQuality.AltTextResult alt = catalog.altText();
    if (alt.sampled() == 0) {
      boolean robots = alt.text().contains("robots.txt");
      return new JourneyCheck(
          "ALT_TEXT",
          "Image alt text (sampled)",
          robots ? CheckStatus.NOT_CHECKED_ROBOTS : CheckStatus.NOT_AVAILABLE,
          null,
          alt.text(),
          null,
          alt.text());
    }
    int score = (int) Math.round(100.0 * alt.productsWithAllAlt() / alt.sampled());
    return checked(
        "ALT_TEXT",
        "Image alt text (sampled)",
        score,
        alt.text(),
        "Add alt text to product images (accessibility and image search)");
  }

  private JourneyCheck descriptions(StoreView store, CatalogQuality catalog) {
    int total = store.products().size();
    if (total == 0) {
      return notAvailable("DESCRIPTION_DEPTH", "Description depth", "No products read");
    }
    int score = (int) Math.round(100.0 * (total - catalog.thinDescriptions()) / total);
    return checked(
        "DESCRIPTION_DEPTH",
        "Description depth",
        score,
        catalog.thinDescriptions()
            + " of "
            + total
            + " products have descriptions under "
            + assumptions.catalog().thinDescriptionChars()
            + " characters",
        "Expand short descriptions with fit, material and care details");
  }

  private static JourneyCheck sizeGuide(List<ProductPageSignals> pages) {
    List<ProductPageSignals> fetched = fetched(pages);
    if (fetched.isEmpty()) {
      return notAvailable(
          "SIZE_GUIDE", "Size guide (sampled pages)", "No product pages could be sampled");
    }
    long with = fetched.stream().filter(ProductPageSignals::sizeGuide).count();
    int score = (int) Math.round(100.0 * with / fetched.size());
    return checked(
        "SIZE_GUIDE",
        "Size guide (sampled pages)",
        score,
        with + " of " + fetched.size() + " sampled product pages mention a size guide or chart",
        "Add a size guide to product pages to reduce wrong-size orders");
  }

  // --- Size & stock ---

  private JourneyCheck variantsSoldOut(Kpis kpis) {
    int score =
        assumptions.journeyThresholds().variantsSoldOutPercent().score(kpis.sizesSoldOutPct());
    return checked(
        "VARIANTS_SOLD_OUT",
        "Sizes sold out",
        score,
        kpis.sizesSoldOutPct()
            + "% of all size/colour variants are sold out ("
            + kpis.soldOutVariants()
            + ")",
        "Restock the sold-out sizes that sell best (see the restock list)");
  }

  private JourneyCheck coreGaps(SizeSummary sizes) {
    if (sizes.productsAnalysed() == 0) {
      return notAvailable(
          "CORE_SIZE_GAPS", "Core sizes missing", "No products with recognised sizes");
    }
    double pct = 100.0 * sizes.productsMissingCoreSizes() / sizes.productsAnalysed();
    int score = assumptions.journeyThresholds().coreSizeGapPercent().score(pct);
    return checked(
        "CORE_SIZE_GAPS",
        "Core sizes missing",
        score,
        sizes.productsMissingCoreSizes()
            + " of "
            + sizes.productsAnalysed()
            + " sized products are missing a core size while other sizes are in stock",
        "Prioritise restocking core (middle) sizes; they sell the most");
  }

  // --- Cart & checkout ---

  private JourneyCheck freeShipping(PricingInsights pricing) {
    PricingInsights.FreeShipping fs = pricing.freeShipping();
    if (fs.text() == null) {
      return notAvailable(
          "FREE_SHIPPING", "Free-shipping threshold", "No free-shipping offer found in store text");
    }
    if (fs.threshold() == null || fs.ratio() == null) {
      return new JourneyCheck(
          "FREE_SHIPPING",
          "Free-shipping threshold",
          CheckStatus.NOT_AVAILABLE,
          null,
          "Free shipping is mentioned without a clear amount: \"" + fs.text() + "\"",
          null,
          "No amount stated");
    }
    int score = assumptions.journeyThresholds().freeShippingToMedianPriceRatio().score(fs.ratio());
    return checked(
        "FREE_SHIPPING",
        "Free-shipping threshold",
        score,
        "Free shipping from "
            + fs.threshold().setScale(2, RoundingMode.HALF_UP).toPlainString()
            + " vs median product price "
            + fs.medianPrice().setScale(2, RoundingMode.HALF_UP).toPlainString()
            + " ("
            + fs.ratio()
            + "×), source "
            + fs.source(),
        "Set the free-shipping threshold near one typical order so most baskets qualify");
  }

  private JourneyCheck returns(PolicySignals returns) {
    String label = "Returns window";
    return switch (returns.status()) {
      case BLOCKED_BY_ROBOTS ->
          new JourneyCheck(
              "RETURNS_WINDOW",
              label,
              CheckStatus.NOT_CHECKED_ROBOTS,
              null,
              "Not checked: the policy page is blocked by robots.txt and no footer page stated a window",
              null,
              "robots.txt: " + returns.robotsRule());
      case FETCHED, FETCHED_ALTERNATIVE -> {
        if (returns.returnDays() == null) {
          String reason =
              "The returns page ("
                  + returns.path()
                  + ") doesn't state a number of days"
                  + (returns.robotsRule() != null
                      ? "; the policy page is blocked by robots.txt (" + returns.robotsRule() + ")"
                      : "");
          yield notAvailable("RETURNS_WINDOW", label, reason);
        }
        int score = assumptions.journeyThresholds().returnsWindowDays().score(returns.returnDays());
        boolean alternative = returns.status() == SourceStatus.FETCHED_ALTERNATIVE;
        String evidence =
            returns.returnDays()
                + "-day returns ("
                + returns.path()
                + ")"
                + (Boolean.TRUE.equals(returns.freeReturns()) ? ", free returns" : "");
        yield new JourneyCheck(
            "RETURNS_WINDOW",
            label,
            alternative ? CheckStatus.CHECKED_VIA_ALTERNATIVE : CheckStatus.CHECKED,
            score,
            evidence,
            score >= GOOD_ENOUGH
                ? null
                : "Consider a longer returns window; short windows deter first-time buyers",
            alternative && returns.robotsRule() != null
                ? "Policy page blocked by robots.txt ("
                    + returns.robotsRule()
                    + "); read "
                    + returns.path()
                    + " instead"
                : null);
      }
      default -> notAvailable("RETURNS_WINDOW", label, "No returns policy page found");
    };
  }

  // --- Come back ---

  private JourneyCheck notifyMe(List<ProductPageSignals> pages, Set<String> apps) {
    List<ProductPageSignals> fetched = fetched(pages);
    if (fetched.isEmpty()) {
      return notAvailable(
          "NOTIFY_ME",
          "\"Notify me\" on sold-out pages",
          "No sold-out product pages could be sampled");
    }
    long with = fetched.stream().filter(ProductPageSignals::notifyMeText).count();
    if (with == 0
        && apps.stream()
            .anyMatch(
                a ->
                    assumptions.detection().wishlistApps().contains(a)
                        || assumptions.detection().emailCaptureApps().contains(a))) {
      return new JourneyCheck(
          "NOTIFY_ME",
          "\"Notify me\" on sold-out pages",
          CheckStatus.NOT_VERIFIABLE,
          null,
          "No \"notify me\" text in the page HTML, but an app that can add it is installed",
          null,
          "It may render with JavaScript, which Beacon Lite doesn't run");
    }
    int score = (int) Math.round(100.0 * with / fetched.size());
    return checked(
        "NOTIFY_ME",
        "\"Notify me\" on sold-out pages",
        score,
        with
            + " of "
            + fetched.size()
            + " sampled sold-out pages offer \"notify me\" / back-in-stock alerts",
        "Add a back-in-stock \"notify me\" button to sold-out product pages");
  }

  // --- helpers ---

  private static JourneyCheck appCheck(
      String key,
      String label,
      Set<String> apps,
      List<String> wanted,
      HomePageSignals home,
      String fix) {
    if (home.status() != SourceStatus.FETCHED) {
      return notRun(key, label, home.status(), home.robotsRule(), "home page");
    }
    List<String> found = wanted.stream().filter(apps::contains).toList();
    if (found.isEmpty()) {
      return checked(key, label, 0, "Not found in the home page or sampled product page HTML", fix);
    }
    return checked(
        key,
        label,
        100,
        "Detected: " + String.join(", ", found).toLowerCase(Locale.ROOT).replace('_', ' '),
        fix);
  }

  private static Set<String> apps(StorefrontSignals s) {
    Set<String> apps = new HashSet<>(s.home().apps());
    s.productPages().forEach(p -> apps.addAll(p.apps()));
    return apps;
  }

  private static List<ProductPageSignals> fetched(List<ProductPageSignals> pages) {
    return pages.stream().filter(p -> p.status() == SourceStatus.FETCHED).toList();
  }

  private static JourneyCheck checked(
      String key, String label, int score, String evidence, String fix) {
    return new JourneyCheck(
        key, label, CheckStatus.CHECKED, score, evidence, score >= GOOD_ENOUGH ? null : fix, null);
  }

  private static JourneyCheck notAvailable(String key, String label, String reason) {
    return new JourneyCheck(key, label, CheckStatus.NOT_AVAILABLE, null, reason, null, reason);
  }

  private static JourneyCheck notRun(
      String key, String label, SourceStatus status, String rule, String what) {
    if (status == SourceStatus.BLOCKED_BY_ROBOTS) {
      return new JourneyCheck(
          key,
          label,
          CheckStatus.NOT_CHECKED_ROBOTS,
          null,
          "Not checked: the " + what + " is blocked by robots.txt",
          null,
          "robots.txt: " + rule);
    }
    return notAvailable(key, label, "The " + what + " couldn't be read");
  }

  private static String mb(long bytes) {
    return String.format(Locale.ROOT, "%.2f MB", bytes / 1_000_000.0);
  }
}

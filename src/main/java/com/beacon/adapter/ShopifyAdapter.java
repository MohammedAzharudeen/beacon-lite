package com.beacon.adapter;

import com.beacon.adapter.model.AltTextSample;
import com.beacon.adapter.model.CatalogInfo;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.CollectionData;
import com.beacon.adapter.model.CollectionsData;
import com.beacon.adapter.model.HomePageSignals;
import com.beacon.adapter.model.PolicySignals;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.ProductPageSignals;
import com.beacon.adapter.model.RobotsInfo;
import com.beacon.adapter.model.SearchSignals;
import com.beacon.adapter.model.ShippingSignals;
import com.beacon.adapter.model.SourceStatus;
import com.beacon.adapter.model.StoreInfo;
import com.beacon.adapter.model.StorefrontSignals;
import com.beacon.adapter.parse.ShopifyJsonParser;
import com.beacon.adapter.parse.ShopifyJsonParser.AltCount;
import com.beacon.adapter.parse.ShopifyJsonParser.CollectionRef;
import com.beacon.adapter.parse.ShopifyJsonParser.ProductPage;
import com.beacon.adapter.parse.StorefrontHtmlParser;
import com.beacon.adapter.parse.StorefrontHtmlParser.FreeShipping;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.Assumptions;
import com.beacon.fetch.FetchOutcome;
import com.beacon.fetch.FetchResult;
import com.beacon.fetch.FetchSession;
import com.beacon.insight.ExclusionRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads standard Shopify storefronts through their public JSON feeds and pages. Every request is
 * checked against robots.txt by the {@link FetchSession}.
 */
@Component
public class ShopifyAdapter implements StoreAdapter {

  private static final Logger log = LoggerFactory.getLogger(ShopifyAdapter.class);
  static final int MAX_CATALOG_PAGES = 100; // Shopify's public feed stops after page 100
  private static final int MAX_COLLECTION_INDEX_PAGES = 10;
  private static final int MAX_BEST_SELLER_PAGES = 20;
  private static final String REFUND_POLICY = "/policies/refund-policy";
  private static final String SHIPPING_POLICY = "/policies/shipping-policy";

  private final ShopifyJsonParser json;
  private final StorefrontHtmlParser html;
  private final Assumptions assumptions;
  private final ExclusionRules exclusions;
  private final Pattern bestSellerPattern;
  private final Pattern botProtection;
  private final Clock clock;

  public ShopifyAdapter(ObjectMapper mapper, Assumptions assumptions, Clock clock) {
    this.json = new ShopifyJsonParser(mapper);
    this.html = new StorefrontHtmlParser(assumptions.detection());
    this.assumptions = assumptions;
    this.exclusions = new ExclusionRules(assumptions.exclusions());
    this.bestSellerPattern =
        Pattern.compile(assumptions.bestSellerCollectionPattern(), Pattern.CASE_INSENSITIVE);
    this.botProtection =
        Pattern.compile(assumptions.detection().botProtection(), Pattern.CASE_INSENSITIVE);
    this.clock = clock;
  }

  @Override
  public Platform platform() {
    return Platform.SHOPIFY;
  }

  /**
   * A Shopify feed answers {@code /products.json} with HTTP 200, a JSON content type and a {@code
   * products} array. Status alone isn't enough: some non-Shopify sites return 200 HTML.
   */
  @Override
  public boolean supports(FetchSession session) {
    return probe(session) == FeedProbe.FEED;
  }

  /** What the store's {@code /products.json} answered. */
  public FeedProbe probe(FetchSession session) {
    FetchResult result = session.get("/products.json?limit=1");
    if (result.isOk() && result.isJson() && json.isProductFeed(result.body())) {
      return FeedProbe.FEED;
    }
    boolean refused = result.status() == 403 || result.status() == 429 || result.status() == 503;
    if (refused && result.body() != null && botProtection.matcher(result.body()).find()) {
      log.info("[DETECT] store={} bot protection page", session.store().host());
      return FeedProbe.BOT_PROTECTION;
    }
    return FeedProbe.NO_FEED;
  }

  /** Outcome of the catalog feed check. */
  public enum FeedProbe {
    FEED,
    BOT_PROTECTION,
    NO_FEED
  }

  @Override
  public CatalogSnapshotData fetchCatalog(FetchSession session, ProgressListener progress) {
    if (session.robotsState() == RobotsInfo.State.UNAVAILABLE) {
      throw new BeaconException(ErrorCode.ROBOTS_UNAVAILABLE);
    }
    List<ProductData> products = new ArrayList<>();
    int skipped = 0;
    int pages = 0;
    boolean capped = false;
    for (int page = 1; page <= MAX_CATALOG_PAGES; page++) {
      FetchResult result = session.get("/products.json?limit=250&page=" + page);
      if (result.outcome() == FetchOutcome.BLOCKED_BY_ROBOTS) {
        throw new BeaconException(
            ErrorCode.CATALOG_FEED_UNAVAILABLE,
            "The store's robots.txt blocks its catalog feed",
            "Beacon Lite never reads pages that robots.txt disallows",
            null);
      }
      if (result.status() == 403
          && result.body() != null
          && botProtection.matcher(result.body()).find()) {
        throw new BeaconException(ErrorCode.STORE_BLOCKS_AUTOMATION);
      }
      if (!result.isOk() || !result.isJson()) {
        // A partial catalog would look like mass removals in the diff, so the run fails instead
        throw new BeaconException(
            ErrorCode.STORE_NOT_REACHABLE,
            "Catalog page " + page + " couldn't be read (" + describe(result) + ")",
            "Try again later; the previous results stay visible",
            null);
      }
      ProductPage parsed = json.parseProducts(result.body());
      pages++;
      skipped += parsed.skipped();
      if (parsed.products().isEmpty() && parsed.skipped() == 0) {
        break;
      }
      products.addAll(parsed.products());
      progress.onProgress(products.size(), null);
      if (page == MAX_CATALOG_PAGES) {
        capped = true;
      }
    }
    log.info(
        "[CATALOG] store={} products={} pages={} skipped={} capped={}",
        session.store().host(),
        products.size(),
        pages,
        skipped,
        capped);
    return new CatalogSnapshotData(
        CatalogSnapshotData.SCHEMA_VERSION,
        new StoreInfo(session.store().host(), Platform.SHOPIFY, null),
        clock.instant(),
        session.robotsInfo(),
        new CatalogInfo(pages, capped, false, skipped),
        products,
        CollectionsData.empty(),
        null);
  }

  @Override
  public CatalogSnapshotData fetchSignals(FetchSession session, CatalogSnapshotData catalog) {
    List<CollectionData> bestSellers = bestSellerCollections(session);
    Page home = page(session, "/");
    HomePageSignals homeSignals = homeSignals(home);
    String currency = home.ok() ? html.currency(home.body()) : null;
    Set<String> bestSellerHandles = new HashSet<>();
    bestSellers.forEach(c -> bestSellerHandles.add(c.handle()));
    List<CollectionData> featured = featuredCollections(session, homeSignals, bestSellerHandles);
    List<String> footer = homeSignals.footerPages();

    StorefrontSignals signals =
        new StorefrontSignals(
            homeSignals,
            returnsPolicy(session, footer),
            shipping(session, home, footer),
            search(session, catalog.products()),
            soldOutPages(session, catalog.products()),
            altText(session, catalog.products()));
    return catalog
        .withSignals(signals, new CollectionsData(bestSellers, featured), session.robotsInfo())
        .withCurrency(currency);
  }

  private List<CollectionData> bestSellerCollections(FetchSession session) {
    List<CollectionRef> all = new ArrayList<>();
    for (int page = 1; page <= MAX_COLLECTION_INDEX_PAGES; page++) {
      Page index = page(session, "/collections.json?limit=250&page=" + page);
      List<CollectionRef> refs = index.ok() ? safeCollections(index.body()) : List.of();
      if (refs.isEmpty()) {
        break;
      }
      all.addAll(refs);
    }
    List<CollectionData> out = new ArrayList<>();
    for (CollectionRef ref : all) {
      if (bestSellerPattern.matcher(ref.handle()).find()) {
        out.add(collection(session, ref.handle(), ref.title(), MAX_BEST_SELLER_PAGES));
      }
    }
    return out;
  }

  private List<CollectionData> featuredCollections(
      FetchSession session, HomePageSignals home, Set<String> bestSellerHandles) {
    List<CollectionData> out = new ArrayList<>();
    for (String handle : home.collectionHandles()) {
      if (out.size() >= assumptions.samples().featuredCollections()) {
        break;
      }
      if (bestSellerHandles.contains(handle) || handle.equals("all")) {
        continue;
      }
      out.add(collection(session, handle, handle, assumptions.samples().featuredCollectionPages()));
    }
    return out;
  }

  private CollectionData collection(
      FetchSession session, String handle, String title, int maxPages) {
    List<Long> ids = new ArrayList<>();
    boolean complete = false;
    for (int page = 1; page <= maxPages; page++) {
      Page result =
          page(
              session,
              "/collections/"
                  + PathEncoder.quote(handle)
                  + "/products.json?limit=250&page="
                  + page);
      if (!result.ok()) {
        break;
      }
      List<ProductData> products = safeProducts(result.body());
      if (products.isEmpty()) {
        complete = true;
        break;
      }
      products.forEach(p -> ids.add(p.externalId()));
    }
    return new CollectionData(handle, title, ids, complete);
  }

  private HomePageSignals homeSignals(Page home) {
    if (!home.ok()) {
      return HomePageSignals.unavailable(home.status(), home.robotsRule());
    }
    String body = home.body();
    List<String> footer = html.policyPageHandles(body);
    int maxFooter = assumptions.samples().footerPages();
    return new HomePageSignals(
        SourceStatus.FETCHED,
        null,
        home.bytes(),
        html.scriptCount(body),
        html.productHandles(body),
        html.collectionHandles(body),
        footer.subList(0, Math.min(maxFooter, footer.size())),
        html.apps(body),
        html.hasNotifyMeText(body),
        html.siteName(body));
  }

  private PolicySignals returnsPolicy(FetchSession session, List<String> footer) {
    Page policy = page(session, REFUND_POLICY);
    if (policy.ok()) {
      String text = html.text(policy.body());
      Integer days = html.returnDays(text);
      if (days != null) {
        return new PolicySignals(
            SourceStatus.FETCHED, REFUND_POLICY, null, days, html.freeReturns(text));
      }
    }
    PolicySignals withoutWindow = null;
    for (String handle : footer) {
      if (!html.isReturnsPage(handle)) {
        continue;
      }
      String path = "/pages/" + handle;
      Page alternative = page(session, path);
      if (alternative.ok()) {
        String text = html.text(alternative.body());
        Integer days = html.returnDays(text);
        PolicySignals found =
            new PolicySignals(
                SourceStatus.FETCHED_ALTERNATIVE,
                path,
                policy.robotsRule(),
                days,
                html.freeReturns(text));
        if (days != null) {
          return found;
        }
        if (withoutWindow == null) {
          withoutWindow = found;
        }
      }
    }
    if (withoutWindow != null) {
      return withoutWindow;
    }
    if (policy.status() == SourceStatus.BLOCKED_BY_ROBOTS) {
      return new PolicySignals(
          SourceStatus.BLOCKED_BY_ROBOTS, REFUND_POLICY, policy.robotsRule(), null, null);
    }
    return new PolicySignals(SourceStatus.NOT_FOUND, REFUND_POLICY, null, null, null);
  }

  private ShippingSignals shipping(FetchSession session, Page home, List<String> footer) {
    List<ShippingSignals> found = new ArrayList<>();
    if (home.ok()) {
      addShipping(found, html.freeShipping(html.text(home.body())), "/", SourceStatus.FETCHED);
    }
    if (found.stream().noneMatch(s -> s.threshold() != null)) {
      Page policy = page(session, SHIPPING_POLICY);
      if (policy.ok()) {
        addShipping(
            found,
            html.freeShipping(html.text(policy.body())),
            SHIPPING_POLICY,
            SourceStatus.FETCHED);
      }
    }
    for (String handle : footer) {
      if (found.stream().anyMatch(s -> s.threshold() != null)) {
        break;
      }
      if (html.isShippingPage(handle)) {
        String path = "/pages/" + handle;
        Page page = page(session, path);
        if (page.ok()) {
          addShipping(
              found,
              html.freeShipping(html.text(page.body())),
              path,
              SourceStatus.FETCHED_ALTERNATIVE);
        }
      }
    }
    return found.stream()
        .filter(s -> s.threshold() != null)
        .findFirst()
        .or(() -> found.stream().findFirst())
        .orElse(new ShippingSignals(SourceStatus.NOT_FOUND, null, null, null));
  }

  private static void addShipping(
      List<ShippingSignals> found, FreeShipping offer, String source, SourceStatus status) {
    if (offer != null) {
      found.add(new ShippingSignals(status, offer.text(), offer.threshold(), source));
    }
  }

  private SearchSignals search(FetchSession session, List<ProductData> products) {
    List<String> terms = searchTerms(products, assumptions.search().termCount());
    if (terms.isEmpty()) {
      return new SearchSignals(SourceStatus.NOT_FOUND, null, List.of());
    }
    List<SearchSignals.SearchProbe> probes = new ArrayList<>();
    for (String term : terms) {
      Page result =
          page(
              session,
              "/search/suggest.json?q=" + PathEncoder.quote(term) + "&resources%5Btype%5D=product");
      if (result.status() == SourceStatus.BLOCKED_BY_ROBOTS) {
        return new SearchSignals(SourceStatus.BLOCKED_BY_ROBOTS, result.robotsRule(), List.of());
      }
      if (result.ok()) {
        try {
          probes.add(json.parseSearch(result.body(), term));
        } catch (BeaconException e) {
          log.warn("[SEARCH] store={} term=\"{}\" unreadable", session.store().host(), term);
        }
      }
    }
    return new SearchSignals(
        probes.isEmpty() ? SourceStatus.FAILED : SourceStatus.FETCHED, null, probes);
  }

  /** The store's most common product types, lower-cased; ties broken alphabetically. */
  static List<String> searchTerms(List<ProductData> products, int count) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (ProductData p : products) {
      String type = p.productType() == null ? "" : p.productType().trim().toLowerCase(Locale.ROOT);
      if (!type.isEmpty()) {
        counts.merge(type, 1, Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(count)
        .map(Map.Entry::getKey)
        .toList();
  }

  private List<ProductPageSignals> soldOutPages(FetchSession session, List<ProductData> products) {
    List<ProductPageSignals> out = new ArrayList<>();
    for (ProductData p : products) {
      if (out.size() >= assumptions.samples().soldOutProductPages()) {
        break;
      }
      if (!p.fullySoldOut() || exclusions.basicExclusion(p).isPresent()) {
        continue;
      }
      Page page = page(session, "/products/" + PathEncoder.quote(p.handle()));
      if (page.ok()) {
        String body = page.body();
        out.add(
            new ProductPageSignals(
                p.handle(),
                SourceStatus.FETCHED,
                page.bytes(),
                html.hasNotifyMeText(body),
                html.hasSizeGuide(body),
                html.apps(body)));
      } else {
        out.add(new ProductPageSignals(p.handle(), page.status(), 0, false, false, Set.of()));
      }
    }
    return out;
  }

  private AltTextSample altText(FetchSession session, List<ProductData> products) {
    int sampled = 0;
    int allAlt = 0;
    int images = 0;
    int withAlt = 0;
    SourceStatus blocked = null;
    int limit = Math.min(assumptions.samples().altTextProducts(), products.size());
    for (ProductData p : products.subList(0, limit)) {
      Page page = page(session, "/products/" + PathEncoder.quote(p.handle()) + ".json");
      if (page.status() == SourceStatus.BLOCKED_BY_ROBOTS) {
        blocked = SourceStatus.BLOCKED_BY_ROBOTS;
        continue;
      }
      if (!page.ok()) {
        continue;
      }
      try {
        AltCount count = json.parseAltText(page.body());
        sampled++;
        images += count.images();
        withAlt += count.withAlt();
        if (count.images() > 0 && count.withAlt() == count.images()) {
          allAlt++;
        }
      } catch (BeaconException e) {
        log.warn("[ALT_TEXT] store={} product={} unreadable", session.store().host(), p.handle());
      }
    }
    if (sampled == 0) {
      return AltTextSample.none(blocked != null ? blocked : SourceStatus.NOT_FOUND);
    }
    return new AltTextSample(SourceStatus.FETCHED, sampled, allAlt, images, withAlt);
  }

  private List<CollectionRef> safeCollections(String body) {
    try {
      return json.parseCollections(body);
    } catch (BeaconException e) {
      return List.of();
    }
  }

  private List<ProductData> safeProducts(String body) {
    try {
      return json.parseProducts(body).products();
    } catch (BeaconException e) {
      return List.of();
    }
  }

  /** Fetches a storefront page; a failed signal page never fails the scan. */
  private static Page page(FetchSession session, String path) {
    try {
      FetchResult result = session.get(path);
      return switch (result.outcome()) {
        case BLOCKED_BY_ROBOTS ->
            new Page(SourceStatus.BLOCKED_BY_ROBOTS, null, 0, result.robotsRule());
        case TOO_LARGE -> new Page(SourceStatus.FAILED, null, result.bytes(), null);
        case RESPONSE ->
            result.isOk()
                ? new Page(SourceStatus.FETCHED, result.body(), result.bytes(), null)
                : new Page(
                    result.status() == 404 || result.status() == 410
                        ? SourceStatus.NOT_FOUND
                        : SourceStatus.FAILED,
                    null,
                    0,
                    null);
      };
    } catch (BeaconException e) {
      log.warn("[SIGNALS] store={} path={} failed code={}", session.store().host(), path, e.code());
      return new Page(SourceStatus.FAILED, null, 0, null);
    }
  }

  private static String describe(FetchResult result) {
    return switch (result.outcome()) {
      case TOO_LARGE -> "response too large";
      case BLOCKED_BY_ROBOTS -> "blocked by robots.txt";
      case RESPONSE -> result.isOk() ? "not JSON" : "HTTP " + result.status();
    };
  }

  private record Page(SourceStatus status, String body, long bytes, String robotsRule) {
    boolean ok() {
      return status == SourceStatus.FETCHED && body != null;
    }
  }
}

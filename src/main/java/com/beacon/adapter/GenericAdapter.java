package com.beacon.adapter;

import com.beacon.adapter.model.AltTextSample;
import com.beacon.adapter.model.CatalogInfo;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.adapter.model.CollectionsData;
import com.beacon.adapter.model.HomePageSignals;
import com.beacon.adapter.model.PolicySignals;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.RobotsInfo;
import com.beacon.adapter.model.SearchSignals;
import com.beacon.adapter.model.ShippingSignals;
import com.beacon.adapter.model.SourceStatus;
import com.beacon.adapter.model.StoreInfo;
import com.beacon.adapter.model.StorefrontSignals;
import com.beacon.adapter.parse.JsonLdProductParser;
import com.beacon.adapter.parse.StorefrontHtmlParser;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.Assumptions;
import com.beacon.fetch.FetchResult;
import com.beacon.fetch.FetchSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Minimal reader for stores without a catalog feed: robots.txt sitemaps → a sample of product pages
 * → schema.org Product JSON-LD. Results are labelled "Sampled". Where pages carry no priced offers,
 * stock-based insights are reported as not available.
 */
@Component
public class GenericAdapter implements StoreAdapter {

  private static final Logger log = LoggerFactory.getLogger(GenericAdapter.class);
  private static final int DETECTION_SAMPLE = 3;
  private static final int MAX_SITEMAPS = 5;

  private final JsonLdProductParser jsonLd;
  private final StorefrontHtmlParser html;
  private final Assumptions assumptions;
  private final Clock clock;

  public GenericAdapter(ObjectMapper mapper, Assumptions assumptions, Clock clock) {
    this.jsonLd = new JsonLdProductParser(mapper);
    this.html = new StorefrontHtmlParser(assumptions.detection());
    this.assumptions = assumptions;
    this.clock = clock;
  }

  @Override
  public Platform platform() {
    return Platform.GENERIC;
  }

  @Override
  public boolean supports(FetchSession session) {
    List<String> urls = productUrls(session, DETECTION_SAMPLE);
    for (String url : urls) {
      if (product(session, url).isPresent()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public CatalogSnapshotData fetchCatalog(FetchSession session, ProgressListener progress) {
    if (session.robotsState() == RobotsInfo.State.UNAVAILABLE) {
      throw new BeaconException(ErrorCode.ROBOTS_UNAVAILABLE);
    }
    int sample = assumptions.samples().genericProductPages();
    List<ProductData> products = new ArrayList<>();
    for (String url : productUrls(session, sample)) {
      product(session, url).ifPresent(products::add);
      progress.onProgress(products.size(), sample);
    }
    if (products.isEmpty()) {
      throw new BeaconException(ErrorCode.PLATFORM_NOT_SUPPORTED);
    }
    log.info("[CATALOG] store={} sampled products={}", session.store().host(), products.size());
    return new CatalogSnapshotData(
        CatalogSnapshotData.SCHEMA_VERSION,
        new StoreInfo(session.store().host(), Platform.GENERIC, null),
        clock.instant(),
        session.robotsInfo(),
        new CatalogInfo(0, false, true, 0),
        products,
        CollectionsData.empty(),
        null);
  }

  @Override
  public CatalogSnapshotData fetchSignals(FetchSession session, CatalogSnapshotData catalog) {
    Optional<FetchResult> fetched = safeGet(session, "/");
    HomePageSignals homeSignals;
    ShippingSignals shipping = new ShippingSignals(SourceStatus.NOT_FOUND, null, null, null);
    if (fetched.isPresent() && fetched.get().isOk()) {
      FetchResult home = fetched.get();
      String body = home.body();
      homeSignals =
          new HomePageSignals(
              SourceStatus.FETCHED,
              null,
              home.bytes(),
              html.scriptCount(body),
              List.of(),
              List.of(),
              List.of(),
              html.apps(body),
              html.hasNotifyMeText(body),
              html.siteName(body));
      StorefrontHtmlParser.FreeShipping offer = html.freeShipping(html.text(body));
      if (offer != null) {
        shipping = new ShippingSignals(SourceStatus.FETCHED, offer.text(), offer.threshold(), "/");
      }
    } else {
      homeSignals =
          HomePageSignals.unavailable(
              fetched.map(GenericAdapter::statusOf).orElse(SourceStatus.FAILED),
              fetched.map(FetchResult::robotsRule).orElse(null));
    }
    StorefrontSignals signals =
        new StorefrontSignals(
            homeSignals,
            new PolicySignals(SourceStatus.NOT_FOUND, null, null, null, null),
            shipping,
            new SearchSignals(SourceStatus.NOT_FOUND, null, List.of()),
            List.of(),
            AltTextSample.none(SourceStatus.NOT_FOUND));
    return catalog.withSignals(signals, CollectionsData.empty(), session.robotsInfo());
  }

  /** Product page URLs on this store, from the sitemaps listed in robots.txt. */
  private List<String> productUrls(FetchSession session, int limit) {
    List<String> pages = new ArrayList<>();
    List<String> queue = new ArrayList<>(session.sitemaps());
    int read = 0;
    while (!queue.isEmpty() && read < MAX_SITEMAPS && pages.size() < limit) {
      String sitemap = queue.remove(0);
      Optional<String> path = sameHostPath(session, sitemap);
      if (path.isEmpty() || sitemap.endsWith(".gz")) {
        continue;
      }
      Optional<FetchResult> result = safeGet(session, path.get()).filter(FetchResult::isOk);
      read++;
      if (result.isEmpty()) {
        continue;
      }
      Document xml = Jsoup.parse(result.get().body(), "", Parser.xmlParser());
      xml.select("sitemap > loc")
          .forEach(
              loc -> {
                String child = loc.text().trim();
                if (child.toLowerCase(Locale.ROOT).contains("product")) {
                  queue.add(0, child);
                }
              });
      xml.select("url > loc")
          .forEach(
              loc -> {
                if (pages.size() < limit) {
                  pages.add(loc.text().trim());
                }
              });
    }
    return pages;
  }

  private Optional<ProductData> product(FetchSession session, String url) {
    Optional<String> path = sameHostPath(session, url);
    if (path.isEmpty()) {
      return Optional.empty();
    }
    return safeGet(session, path.get())
        .filter(FetchResult::isOk)
        .flatMap(result -> jsonLd.parse(result.body(), url));
  }

  private static Optional<String> sameHostPath(FetchSession session, String url) {
    try {
      URI uri = URI.create(url);
      if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(session.store().host())) {
        return Optional.empty();
      }
      String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
      return Optional.of(uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery());
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<FetchResult> safeGet(FetchSession session, String path) {
    try {
      return Optional.of(session.get(path));
    } catch (BeaconException e) {
      log.warn("[GENERIC] store={} path={} failed code={}", session.store().host(), path, e.code());
      return Optional.empty();
    }
  }

  private static SourceStatus statusOf(FetchResult result) {
    return switch (result.outcome()) {
      case BLOCKED_BY_ROBOTS -> SourceStatus.BLOCKED_BY_ROBOTS;
      case TOO_LARGE -> SourceStatus.FAILED;
      case RESPONSE -> result.status() == 404 ? SourceStatus.NOT_FOUND : SourceStatus.FAILED;
    };
  }
}

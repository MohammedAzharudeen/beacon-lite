package com.beacon.store;

import com.beacon.adapter.AdapterRegistry;
import com.beacon.adapter.StoreAdapter;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.BeaconProperties;
import com.beacon.fetch.FetchSession;
import com.beacon.fetch.PoliteHttpClient;
import com.beacon.job.JobService;
import com.beacon.job.JobType;
import com.beacon.security.StoreUrl;
import com.beacon.security.UrlGuard;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Adds stores and starts their scans. */
@Service
public class StoreService {

  private static final Logger log = LoggerFactory.getLogger(StoreService.class);

  private final StoreRepository stores;
  private final UrlGuard urlGuard;
  private final AdapterRegistry adapters;
  private final PoliteHttpClient http;
  private final JobService jobs;
  private final boolean demo;

  public StoreService(
      StoreRepository stores,
      UrlGuard urlGuard,
      AdapterRegistry adapters,
      PoliteHttpClient http,
      JobService jobs,
      BeaconProperties properties) {
    this.stores = stores;
    this.urlGuard = urlGuard;
    this.adapters = adapters;
    this.http = http;
    this.jobs = jobs;
    this.demo = properties.demo();
  }

  /** The result of adding a store. */
  public record Added(Store store, JobService.StartedJob job) {}

  /**
   * Validates the address, detects the platform and starts the first scan in the background.
   *
   * @throws BeaconException INVALID_URL, URL_NOT_ALLOWED, STORE_ALREADY_TRACKED (with the existing
   *     store id), PLATFORM_NOT_SUPPORTED, CATALOG_FEED_UNAVAILABLE, STORE_BLOCKS_AUTOMATION
   */
  public Added add(String rawUrl) {
    log.info("[ADD_STORE] url={}", rawUrl);
    StoreUrl url = demo ? demoUrl(rawUrl) : urlGuard.checkUserInput(rawUrl);
    stores
        .findByDomain(url.host())
        .ifPresent(
            existing -> {
              throw new BeaconException(
                  ErrorCode.STORE_ALREADY_TRACKED, Map.of("storeId", existing.getId()));
            });
    http.refreshRobots(url.host());
    StoreAdapter adapter = adapters.detect(new FetchSession(url, http));
    Store store =
        stores.save(Store.adding(url.host(), defaultName(url.host()), adapter.platform()));
    JobService.StartedJob job = jobs.start(store.getId(), JobType.ADD_STORE);
    return new Added(store, job);
  }

  public List<Store> list() {
    return stores.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
  }

  public Store get(long storeId) {
    return stores.findById(storeId).orElseThrow(() -> new BeaconException(ErrorCode.NOT_FOUND));
  }

  /** "Refresh now": starts a scan or joins the running one. */
  public JobService.StartedJob refresh(long storeId) {
    get(storeId);
    return jobs.start(storeId, JobType.REFRESH);
  }

  /** A readable name until the store's own name is read from its home page. */
  public static String defaultName(String host) {
    String name = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    int dot = name.indexOf('.');
    String label = dot > 0 ? name.substring(0, dot) : name;
    return label.isEmpty() ? host : Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  private StoreUrl demoUrl(String raw) {
    String host =
        raw.trim()
            .toLowerCase(Locale.ROOT)
            .replaceFirst("^https?://", "")
            .replaceFirst("[/?#].*$", "");
    if (host.isEmpty()) {
      throw new BeaconException(ErrorCode.INVALID_URL);
    }
    return new StoreUrl(host, URI.create("https://" + host));
  }
}

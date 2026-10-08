package com.beacon.adapter;

import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.fetch.FetchSession;
import com.beacon.snapshot.SnapshotCodec;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Serves recorded snapshots instead of the network (demo profile and tests). Recordings live in
 * {@code src/main/resources/snapshots/<domain>/<capture time>.json.gz}. Files are only indexed at
 * startup; each is decoded when it is needed and not kept, so many recordings don't fill memory.
 */
@Component
public class ReplayAdapter implements StoreAdapter {

  private static final Logger log = LoggerFactory.getLogger(ReplayAdapter.class);
  private static final String PATTERN = "classpath*:snapshots/*/*.json.gz";

  /** Domain → recordings, oldest first, each decoded on demand. */
  private final Map<String, List<Supplier<CatalogSnapshotData>>> recordings;

  @Autowired
  public ReplayAdapter() {
    this.recordings = indexClasspathRecordings();
  }

  /** Recordings already in memory (tests). */
  public ReplayAdapter(Map<String, List<CatalogSnapshotData>> data) {
    Map<String, List<Supplier<CatalogSnapshotData>>> byDomain = new TreeMap<>();
    data.forEach(
        (domain, list) ->
            byDomain.put(
                key(domain),
                list.stream()
                    .sorted(Comparator.comparing(CatalogSnapshotData::capturedAt))
                    .<Supplier<CatalogSnapshotData>>map(d -> () -> d)
                    .toList()));
    this.recordings = byDomain;
  }

  @Override
  public Platform platform() {
    return Platform.REPLAY;
  }

  @Override
  public boolean supports(FetchSession session) {
    return recordings.containsKey(key(session.store().host()));
  }

  /** Recorded store domains, e.g. {@code www.stevemadden.com}. */
  public List<String> domains() {
    return List.copyOf(recordings.keySet());
  }

  /** How many recordings a store has. */
  public int count(String domain) {
    return recordings.getOrDefault(key(domain), List.of()).size();
  }

  /** One recording of a store, {@code 0} = oldest; decoded now, not cached. */
  public CatalogSnapshotData recording(String domain, int index) {
    return recordings.get(key(domain)).get(index).get();
  }

  /** Returns the newest recording; replays never touch the network. */
  @Override
  public CatalogSnapshotData fetchCatalog(FetchSession session, ProgressListener progress) {
    String domain = session.store().host();
    int n = count(domain);
    if (n == 0) {
      throw new BeaconException(
          ErrorCode.PLATFORM_NOT_SUPPORTED,
          "No recorded snapshot for " + domain,
          "Demo mode only has the recorded demo stores",
          null);
    }
    CatalogSnapshotData latest = recording(domain, n - 1);
    progress.onProgress(latest.productCount(), latest.productCount());
    return latest;
  }

  @Override
  public CatalogSnapshotData fetchSignals(FetchSession session, CatalogSnapshotData catalog) {
    return catalog;
  }

  private static String key(String domain) {
    return domain.toLowerCase(Locale.ROOT);
  }

  /**
   * Indexes {@code snapshots/<domain>/<capture time>.json.gz}: the folder names the store and the
   * file name (an ISO timestamp) gives the order, so nothing is decoded at startup.
   */
  private static Map<String, List<Supplier<CatalogSnapshotData>>> indexClasspathRecordings() {
    Map<String, List<Resource>> files = new TreeMap<>();
    try {
      for (Resource resource : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
        String[] parts = resource.getURL().getPath().split("/");
        String domain = key(parts[parts.length - 2]);
        files.computeIfAbsent(domain, d -> new ArrayList<>()).add(resource);
      }
    } catch (IOException e) {
      throw new BeaconException(
          ErrorCode.PARSE_FAILED, "Recorded snapshots couldn't be read", "Rebuild the app", e);
    }
    Map<String, List<Supplier<CatalogSnapshotData>>> byDomain = new TreeMap<>();
    files.forEach(
        (domain, list) -> {
          list.sort(Comparator.comparing(Resource::getFilename));
          byDomain.put(domain, list.stream().map(r -> decoder(domain, r)).toList());
          log.info("[REPLAY] domain={} recordings={}", domain, list.size());
        });
    return byDomain;
  }

  private static Supplier<CatalogSnapshotData> decoder(String domain, Resource resource) {
    return () -> {
      try (InputStream in = resource.getInputStream()) {
        CatalogSnapshotData data = SnapshotCodec.read(in);
        if (!key(data.store().domain()).equals(domain)) {
          throw new BeaconException(
              ErrorCode.PARSE_FAILED,
              "Recording " + resource.getFilename() + " belongs to " + data.store().domain(),
              "Move it to the folder of its own store",
              null);
        }
        return data;
      } catch (IOException e) {
        throw new BeaconException(
            ErrorCode.PARSE_FAILED,
            "Recorded snapshot " + resource.getFilename() + " couldn't be read",
            "Rebuild the app",
            e);
      }
    };
  }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Serves recorded snapshots instead of the network (demo profile and tests). Recordings live in
 * {@code src/main/resources/snapshots/<domain>/*.json.gz}.
 */
@Component
public class ReplayAdapter implements StoreAdapter {

  private static final Logger log = LoggerFactory.getLogger(ReplayAdapter.class);
  private static final String PATTERN = "classpath*:snapshots/*/*.json.gz";

  private final Map<String, List<CatalogSnapshotData>> recordings;

  @Autowired
  public ReplayAdapter() {
    this(loadClasspathRecordings());
  }

  ReplayAdapter(Map<String, List<CatalogSnapshotData>> recordings) {
    this.recordings = recordings;
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

  /** All recordings of a store, oldest first. */
  public List<CatalogSnapshotData> recordings(String domain) {
    return recordings.getOrDefault(key(domain), List.of());
  }

  /** Returns the newest recording; replays never touch the network. */
  @Override
  public CatalogSnapshotData fetchCatalog(FetchSession session, ProgressListener progress) {
    List<CatalogSnapshotData> list = recordings(session.store().host());
    if (list.isEmpty()) {
      throw new BeaconException(
          ErrorCode.PLATFORM_NOT_SUPPORTED,
          "No recorded snapshot for " + session.store().host(),
          "Demo mode only has the recorded demo stores",
          null);
    }
    CatalogSnapshotData latest = list.get(list.size() - 1);
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

  private static Map<String, List<CatalogSnapshotData>> loadClasspathRecordings() {
    Map<String, List<CatalogSnapshotData>> byDomain = new TreeMap<>();
    try {
      for (Resource resource : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
        try (InputStream in = resource.getInputStream()) {
          CatalogSnapshotData data = SnapshotCodec.read(in);
          byDomain.computeIfAbsent(key(data.store().domain()), d -> new ArrayList<>()).add(data);
        }
      }
    } catch (IOException e) {
      throw new BeaconException(
          ErrorCode.PARSE_FAILED, "Recorded snapshots couldn't be read", "Rebuild the app", e);
    }
    byDomain
        .values()
        .forEach(list -> list.sort(Comparator.comparing(CatalogSnapshotData::capturedAt)));
    byDomain.forEach((d, list) -> log.info("[REPLAY] domain={} recordings={}", d, list.size()));
    return byDomain;
  }
}

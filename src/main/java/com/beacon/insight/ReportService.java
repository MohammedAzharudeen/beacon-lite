package com.beacon.insight;

import com.beacon.action.ActionService;
import com.beacon.adapter.model.CatalogSnapshotData;
import com.beacon.catalog.Variant;
import com.beacon.catalog.VariantRepository;
import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.Assumptions;
import com.beacon.diff.ChangeEvent;
import com.beacon.diff.ChangeEventRepository;
import com.beacon.insight.report.ChangeCounts;
import com.beacon.insight.report.InsightReport;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds, stores and serves insight reports. */
@Service
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private final InsightReportRepository reports;
  private final ChangeEventRepository changes;
  private final VariantRepository variants;
  private final ActionService actions;
  private final Assumptions assumptions;
  private final Clock clock;
  private final Map<Long, InsightReport> latestCache = new ConcurrentHashMap<>();

  public ReportService(
      InsightReportRepository reports,
      ChangeEventRepository changes,
      VariantRepository variants,
      ActionService actions,
      Assumptions assumptions,
      Clock clock) {
    this.reports = reports;
    this.changes = changes;
    this.variants = variants;
    this.actions = actions;
    this.assumptions = assumptions;
    this.clock = clock;
  }

  /**
   * Builds and stores the report for a completed snapshot.
   *
   * @param hasPrevious false on the store's first snapshot
   */
  @Transactional
  public InsightReport generate(
      long storeId, long snapshotId, CatalogSnapshotData data, boolean hasPrevious) {
    long started = System.nanoTime();
    ChangeContext context =
        hasPrevious ? changeContext(storeId, snapshotId) : ChangeContext.firstSnapshot();
    InsightReport report =
        new ReportBuilder(assumptions)
            .build(storeId, snapshotId, data, context, actions.states(storeId), clock.instant());
    reports.save(
        InsightReportEntity.of(
            storeId,
            snapshotId,
            assumptions.version(),
            report.generatedAt(),
            ReportCodec.encode(report)));
    latestCache.put(storeId, report);
    log.info(
        "[REPORT] store={} snapshot={} restock={} gaps={} ms={}",
        storeId,
        snapshotId,
        report.restockTotal(),
        report.sizeGapTotal(),
        (System.nanoTime() - started) / 1_000_000);
    return report;
  }

  /** The latest report with the top 5 re-picked from current action statuses. */
  @Transactional(readOnly = true)
  public Optional<InsightReport> latest(long storeId) {
    InsightReport report =
        latestCache.computeIfAbsent(
            storeId,
            id ->
                reports
                    .findTopByStoreIdOrderByGeneratedAtDesc(id)
                    .map(e -> ReportCodec.decode(e.getPayload()))
                    .orElse(null));
    if (report == null) {
      return Optional.empty();
    }
    return Optional.of(
        report.withTopActions(ReportBuilder.topActions(report.actions(), actions.states(storeId))));
  }

  public InsightReport require(long storeId) {
    return latest(storeId).orElseThrow(() -> new BeaconException(ErrorCode.REPORT_NOT_READY));
  }

  /**
   * A small value from every stored report of a store, oldest first (KPI trend lines). Each report
   * is decoded, reduced to {@code pick}'s result and released before the next, so memory stays flat
   * however many snapshots a store has.
   */
  @Transactional(readOnly = true)
  public <T> List<T> history(long storeId, Function<InsightReport, T> pick) {
    List<T> out = new ArrayList<>();
    for (InsightReportEntity e : reports.findByStoreIdOrderByGeneratedAtAsc(storeId)) {
      out.add(pick.apply(ReportCodec.decode(e.getPayload())));
    }
    return out;
  }

  private ChangeContext changeContext(long storeId, long snapshotId) {
    List<ChangeEvent> events = changes.findByStoreIdAndToSnapshotId(storeId, snapshotId);
    int soldOut = 0;
    int restocked = 0;
    int priceChanges = 0;
    Set<Long> soldOutVariantDbIds = new HashSet<>();
    for (ChangeEvent e : events) {
      switch (e.getType()) {
        case SOLD_OUT -> {
          soldOut++;
          if (e.getVariantId() != null) {
            soldOutVariantDbIds.add(e.getVariantId());
          }
        }
        case RESTOCKED -> restocked++;
        case PRICE_CHANGED -> priceChanges++;
        default -> {
          // additions and removals are listed in the changes timeline, not counted in the KPI
        }
      }
    }
    Set<Long> externalIds = new HashSet<>();
    variants.findAllById(soldOutVariantDbIds).stream()
        .map(Variant::getExternalId)
        .forEach(externalIds::add);
    return new ChangeContext(new ChangeCounts(soldOut, restocked, priceChanges), externalIds);
  }
}

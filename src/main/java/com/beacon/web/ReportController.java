package com.beacon.web;

import com.beacon.action.ActionService;
import com.beacon.action.ActionState;
import com.beacon.action.ActionStatus;
import com.beacon.cli.InsightBriefWriter;
import com.beacon.config.Assumptions;
import com.beacon.diff.ChangeType;
import com.beacon.diff.ChangesService;
import com.beacon.insight.ReportService;
import com.beacon.insight.report.InsightReport;
import com.beacon.insight.report.RestockRow;
import com.beacon.insight.report.SizeCell;
import com.beacon.insight.report.SizeGapRow;
import com.beacon.store.StoreService;
import com.beacon.web.dto.ActionStatusRequest;
import com.beacon.web.dto.ActionStatusResponse;
import com.beacon.web.dto.Page;
import com.beacon.web.dto.TrendsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/stores/{storeId}")
@Tag(
    name = "Reports",
    description = "Insights, restock list, changes, trends, actions and the brief")
public class ReportController {

  private static final int DASHBOARD_ROWS = 100;

  private final ReportService reports;
  private final StoreService stores;
  private final ChangesService changes;
  private final ActionService actions;
  private final Assumptions assumptions;

  public ReportController(
      ReportService reports,
      StoreService stores,
      ChangesService changes,
      ActionService actions,
      Assumptions assumptions) {
    this.reports = reports;
    this.stores = stores;
    this.changes = changes;
    this.actions = actions;
    this.assumptions = assumptions;
  }

  @GetMapping("/report")
  @Operation(
      summary = "Latest insight report (long lists trimmed; page them via /restock and /size-gaps)")
  public InsightReport report(@PathVariable long storeId) {
    stores.get(storeId);
    return reports.require(storeId).trimmed(DASHBOARD_ROWS);
  }

  @GetMapping("/restock")
  @Operation(summary = "Restock candidates, ranked; filter by category and search by name")
  public Page<RestockRow> restock(
      @PathVariable long storeId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    InsightReport r = reports.require(storeId);
    List<RestockRow> rows =
        r.restock().stream()
            .filter(matches(category, q, RestockRow::productType, RestockRow::title))
            .toList();
    return new Page<>(slice(rows, offset, limit), rows.size(), r.currency());
  }

  @GetMapping("/size-gaps")
  @Operation(summary = "Products with sold-out sizes; filter by category and search by name")
  public Page<SizeGapRow> sizeGaps(
      @PathVariable long storeId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    InsightReport r = reports.require(storeId);
    List<SizeGapRow> rows =
        r.sizeGaps().stream()
            .filter(matches(category, q, SizeGapRow::productType, SizeGapRow::title))
            .toList();
    return new Page<>(slice(rows, offset, limit), rows.size(), r.currency());
  }

  @GetMapping(value = "/restock.csv", produces = "text/csv")
  @Operation(summary = "Restock list as CSV (same rows and columns as the table)")
  public ResponseEntity<String> restockCsv(@PathVariable long storeId) {
    InsightReport r = reports.require(storeId);
    StringBuilder csv =
        new StringBuilder(
            "rank,product,type,product_url,sold_out_sizes,total_sizes,sold_out_size_list,price,currency,signals,"
                + "demand_score,gap_score,est_at_risk_per_week,confidence,snapshot_id,assumptions_version\n");
    int rank = 1;
    for (RestockRow row : r.restock()) {
      csv.append(rank++)
          .append(',')
          .append(cell(row.title()))
          .append(',')
          .append(cell(row.productType()))
          .append(',')
          .append(cell(row.productUrl()))
          .append(',')
          .append(row.soldOutSizes())
          .append(',')
          .append(row.totalSizes())
          .append(',')
          .append(
              cell(
                  row.sizes().stream()
                      .filter(c -> !c.available())
                      .map(SizeCell::label)
                      .collect(Collectors.joining(" "))))
          .append(',')
          .append(row.price().amount().toPlainString())
          .append(',')
          .append(cell(r.currency()))
          .append(',')
          .append(cell(row.signals().stream().map(Enum::name).collect(Collectors.joining(" "))))
          .append(',')
          .append(row.demandScore())
          .append(',')
          .append(row.gapScore())
          .append(',')
          .append(row.atRiskPerWeek().amount())
          .append(',')
          .append(row.confidence())
          .append(',')
          .append(r.snapshotId())
          .append(',')
          .append(r.assumptionsVersion())
          .append('\n');
    }
    String file = r.domain().replaceFirst("^www\\.", "") + "-restock.csv";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
        .contentType(new MediaType("text", "csv"))
        .body(csv.toString());
  }

  @GetMapping("/changes")
  @Operation(summary = "Change events between snapshots, newest window first")
  public ChangesService.Changes changes(
      @PathVariable long storeId,
      @RequestParam(required = false) ChangeType type,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
    stores.get(storeId);
    return changes.recent(storeId, type, limit);
  }

  @GetMapping("/trends")
  @Operation(summary = "KPI values for every snapshot, oldest first")
  public TrendsResponse trends(@PathVariable long storeId) {
    stores.get(storeId);
    return new TrendsResponse(
        reports.history(
            storeId,
            r ->
                new TrendsResponse.Point(
                    r.snapshotId(),
                    r.capturedAt(),
                    r.kpis().sizesSoldOutPct(),
                    r.journeyScore(),
                    r.kpis().atRiskPerWeek().amount(),
                    r.kpis().soldOutVariants())));
  }

  @PutMapping("/actions/{actionKey}")
  @Operation(summary = "Set an action's status: TODO, DONE or DISMISSED")
  public ActionStatusResponse setAction(
      @PathVariable long storeId,
      @PathVariable String actionKey,
      @Valid @RequestBody ActionStatusRequest request) {
    ActionStatus saved = actions.set(storeId, actionKey, ActionState.valueOf(request.status()));
    return new ActionStatusResponse(
        saved.getActionKey(), saved.getStatus().name(), saved.getUpdatedAt());
  }

  @GetMapping(value = "/brief", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(summary = "Print-ready Insight Brief (HTML)")
  public String brief(@PathVariable long storeId) {
    return InsightBriefWriter.html(
        reports.require(storeId), stores.get(storeId).getDisplayName(), assumptions);
  }

  private static <T> Predicate<T> matches(
      String category, String q, Function<T, String> type, Function<T, String> title) {
    String c = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
    String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    return row -> {
      String t = type.apply(row) == null ? "" : type.apply(row).toLowerCase(Locale.ROOT);
      String name = title.apply(row) == null ? "" : title.apply(row).toLowerCase(Locale.ROOT);
      return (c.isEmpty() || t.equals(c)) && (text.isEmpty() || name.contains(text));
    };
  }

  private static <T> List<T> slice(List<T> rows, int offset, int limit) {
    if (offset >= rows.size()) {
      return List.of();
    }
    return rows.subList(offset, Math.min(rows.size(), offset + limit));
  }

  /**
   * CSV cell with quotes; leading = + - @ are prefixed so spreadsheets don't run them as formulas.
   */
  static String cell(String value) {
    if (value == null) {
      return "";
    }
    String v = value;
    if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
      v = "'" + v;
    }
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }
}

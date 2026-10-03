package com.beacon.web;

import com.beacon.insight.BenchmarkService;
import com.beacon.insight.ReportService;
import com.beacon.insight.report.InsightReport;
import com.beacon.store.Store;
import com.beacon.store.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/benchmark")
@Tag(name = "Compare", description = "Store comparison on common checks")
public class BenchmarkController {

  private final StoreService stores;
  private final ReportService reports;
  private final BenchmarkService benchmark;

  public BenchmarkController(
      StoreService stores, ReportService reports, BenchmarkService benchmark) {
    this.stores = stores;
    this.reports = reports;
    this.benchmark = benchmark;
  }

  @GetMapping
  @Operation(summary = "Compare stores (default: all with a report)")
  public BenchmarkService.Benchmark compare(@RequestParam(required = false) String storeIds) {
    Set<Long> ids =
        storeIds == null || storeIds.isBlank()
            ? Set.of()
            : Arrays.stream(storeIds.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    List<InsightReport> list = new ArrayList<>();
    for (Store s : stores.list()) {
      if (ids.isEmpty() || ids.contains(s.getId())) {
        reports.latest(s.getId()).ifPresent(list::add);
      }
    }
    return benchmark.compare(list);
  }
}

package com.beacon.chat.tools;

import com.beacon.insight.BenchmarkService;
import com.beacon.insight.ReportService;
import com.beacon.insight.report.InsightReport;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CompareStoresTool implements ChatTool {

  private final StoreRepository stores;
  private final ReportService reports;
  private final BenchmarkService benchmark;

  public CompareStoresTool(
      StoreRepository stores, ReportService reports, BenchmarkService benchmark) {
    this.stores = stores;
    this.reports = reports;
    this.benchmark = benchmark;
  }

  @Override
  public String name() {
    return "compare_stores";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    Set<Long> ids = new HashSet<>();
    args.path("store_ids").forEach(n -> ids.add(n.asLong()));
    List<InsightReport> list = new ArrayList<>();
    for (Store s : stores.findAll()) {
      if (ids.isEmpty() || ids.contains(s.getId()) || s.getId() == ctx.storeId()) {
        reports.latest(s.getId()).ifPresent(list::add);
      }
    }
    return benchmark.compare(list);
  }
}

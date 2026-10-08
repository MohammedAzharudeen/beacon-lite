package com.beacon.web;

import com.beacon.chat.LlmProvider;
import com.beacon.common.Metrics;
import com.beacon.config.Assumptions;
import com.beacon.job.Job;
import com.beacon.job.JobRepository;
import com.beacon.snapshot.DemoSeeder;
import com.beacon.store.Store;
import com.beacon.store.StoreService;
import com.beacon.web.dto.AssumptionsResponse;
import com.beacon.web.dto.HealthResponse;
import com.beacon.web.dto.LlmStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "Assumptions, AI status, health and metrics")
public class SystemController {

  private final Assumptions assumptions;
  private final LlmProvider llm;
  private final StoreService stores;
  private final JobRepository jobs;
  private final Metrics metrics;
  private final DemoSeeder demoSeeder;

  public SystemController(
      Assumptions assumptions,
      LlmProvider llm,
      StoreService stores,
      JobRepository jobs,
      Metrics metrics,
      DemoSeeder demoSeeder) {
    this.assumptions = assumptions;
    this.llm = llm;
    this.stores = stores;
    this.jobs = jobs;
    this.metrics = metrics;
    this.demoSeeder = demoSeeder;
  }

  @GetMapping("/assumptions")
  @Operation(summary = "Every judgment call and its version (hash of assumptions.yml)")
  public AssumptionsResponse assumptions() {
    return new AssumptionsResponse(assumptions.version(), assumptions);
  }

  @GetMapping("/demo/progress")
  @Operation(summary = "Loading of the recorded demo stores: done when loading is false")
  public DemoSeeder.Progress demoProgress() {
    return demoSeeder.progress();
  }

  @GetMapping("/llm/status")
  @Operation(summary = "AI model connection")
  public LlmStatusResponse llmStatus() {
    return new LlmStatusResponse(llm.provider(), llm.baseUrl(), llm.model(), llm.isReachable());
  }

  @GetMapping("/health")
  @Operation(summary = "App status, each store's last run, AI model reachable")
  public HealthResponse health() {
    List<Job> allJobs = jobs.findAll();
    List<HealthResponse.StoreHealth> rows =
        stores.list().stream().map(s -> health(s, allJobs)).toList();
    return new HealthResponse("UP", rows, llm.isReachable());
  }

  @GetMapping("/metrics")
  @Operation(summary = "Fetch, job and chat counters and timings")
  public Map<String, Object> metrics() {
    return metrics.snapshot();
  }

  private static HealthResponse.StoreHealth health(Store store, List<Job> allJobs) {
    Optional<Job> last =
        allJobs.stream()
            .filter(j -> j.getStoreId().equals(store.getId()))
            .max(Comparator.comparing(Job::getCreatedAt));
    return new HealthResponse.StoreHealth(
        store.getId(),
        store.getDomain(),
        last.map(Job::getUpdatedAt).orElse(null),
        last.map(j -> j.getStatus().name()).orElse(store.getStatus().name()));
  }
}

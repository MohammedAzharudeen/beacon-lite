package com.beacon.web;

import com.beacon.common.ErrorCode;
import com.beacon.job.Job;
import com.beacon.job.JobService;
import com.beacon.web.dto.JobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Scan progress")
public class JobController {

  private final JobService jobs;

  public JobController(JobService jobs) {
    this.jobs = jobs;
  }

  @GetMapping("/{jobId}")
  @Operation(summary = "Job progress")
  public JobResponse get(@PathVariable UUID jobId) {
    Job j = jobs.get(jobId);
    JobResponse.JobError error = null;
    if (j.getErrorCode() != null) {
      ErrorCode code = ErrorCode.valueOf(j.getErrorCode());
      error = new JobResponse.JobError(code.name(), code.defaultMessage(), j.getMessage());
    }
    return new JobResponse(
        j.getId(),
        j.getStoreId(),
        j.getType().name(),
        j.getStatus().name(),
        j.getStep() == null ? null : j.getStep().name(),
        j.getProductsRead(),
        j.getProductsEstimate(),
        error);
  }
}

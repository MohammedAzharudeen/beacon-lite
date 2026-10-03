package com.beacon.job;

import com.beacon.common.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Short, separate transactions for job progress, so the UI sees each step as it happens. */
@Component
public class JobProgress {

  private final JobRepository jobs;

  public JobProgress(JobRepository jobs) {
    this.jobs = jobs;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void step(UUID jobId, JobStep step) {
    jobs.findById(jobId).ifPresent(j -> j.startStep(step));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void progress(UUID jobId, int read, Integer estimate) {
    jobs.findById(jobId).ifPresent(j -> j.progress(read, estimate));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void succeed(UUID jobId) {
    jobs.findById(jobId).ifPresent(Job::succeed);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(UUID jobId, ErrorCode code, String hint) {
    jobs.findById(jobId).ifPresent(j -> j.fail(code, hint));
  }
}

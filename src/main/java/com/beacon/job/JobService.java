package com.beacon.job;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.snapshot.SnapshotService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts scans as background jobs with visible progress. */
@Service
public class JobService {

  private static final Logger log = LoggerFactory.getLogger(JobService.class);

  private final JobRepository jobs;
  private final StoreLockRegistry locks;
  private final SnapshotService snapshots;
  private final ThreadPoolTaskExecutor scanExecutor;

  public JobService(
      JobRepository jobs,
      StoreLockRegistry locks,
      SnapshotService snapshots,
      ThreadPoolTaskExecutor scanExecutor) {
    this.jobs = jobs;
    this.locks = locks;
    this.snapshots = snapshots;
    this.scanExecutor = scanExecutor;
  }

  /** A started (or joined) job. */
  public record StartedJob(Job job, boolean joinedExisting) {}

  /** Starts a scan, or joins the one already running for the store. */
  public synchronized StartedJob start(long storeId, JobType type) {
    Job job = Job.queued(storeId, type);
    Optional<UUID> running = locks.claim(storeId, job.getId());
    if (running.isPresent()) {
      Optional<Job> existing = jobs.findById(running.get());
      if (existing.isPresent() && existing.get().isActive()) {
        log.info("[JOB] store={} joined job={}", storeId, running.get());
        return new StartedJob(existing.get(), true);
      }
      locks.release(storeId, running.get());
      locks.claim(storeId, job.getId());
    }
    Job saved = jobs.save(job);
    log.info("[JOB] store={} job={} type={} queued", storeId, saved.getId(), type);
    scanExecutor.execute(
        () -> {
          try {
            snapshots.capture(storeId, saved.getId());
          } finally {
            locks.release(storeId, saved.getId());
          }
        });
    return new StartedJob(saved, false);
  }

  @Transactional(readOnly = true)
  public Job get(UUID jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new BeaconException(ErrorCode.NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Optional<Job> active(long storeId) {
    return jobs.findFirstByStoreIdAndStatusIn(
        storeId, List.of(JobStatus.QUEUED, JobStatus.RUNNING));
  }

  /** Jobs left running by a stopped app can't finish; mark them failed so the UI isn't stuck. */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void failInterruptedJobs() {
    jobs.findAll().stream()
        .filter(Job::isActive)
        .forEach(
            j -> {
              log.warn("[JOB] job={} interrupted by restart", j.getId());
              j.fail(
                  ErrorCode.INTERNAL_ERROR,
                  "The app restarted during this scan; refresh to try again");
            });
  }
}

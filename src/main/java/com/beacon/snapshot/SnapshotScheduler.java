package com.beacon.snapshot;

import com.beacon.config.BeaconProperties;
import com.beacon.job.JobService;
import com.beacon.job.JobType;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.store.StoreStatus;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/** Re-checks every active store on a fixed interval (default 6 hours), then applies retention. */
@Component
public class SnapshotScheduler {

  private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

  private final StoreRepository stores;
  private final JobService jobs;
  private final RetentionService retention;
  private final ThreadPoolTaskScheduler scheduler;
  private final BeaconProperties.Snapshot config;
  private final Clock clock;

  public SnapshotScheduler(
      StoreRepository stores,
      JobService jobs,
      RetentionService retention,
      ThreadPoolTaskScheduler snapshotTaskScheduler,
      BeaconProperties properties,
      Clock clock) {
    this.stores = stores;
    this.jobs = jobs;
    this.retention = retention;
    this.scheduler = snapshotTaskScheduler;
    this.config = properties.snapshot();
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    if (!config.schedulerEnabled()) {
      log.info("[SCHEDULE] disabled");
      return;
    }
    Duration interval = config.interval();
    scheduler.scheduleWithFixedDelay(this::refreshAll, clock.instant().plus(interval), interval);
    log.info("[SCHEDULE] every {} (next run {})", interval, clock.instant().plus(interval));
  }

  /** When the next automatic check happens, for "next check" in the UI. */
  public Duration interval() {
    return config.interval();
  }

  void refreshAll() {
    for (Store store : stores.findAll()) {
      if (store.getStatus() == StoreStatus.ACTIVE) {
        jobs.start(store.getId(), JobType.SCHEDULED);
      }
    }
    retention.applyAll();
  }
}

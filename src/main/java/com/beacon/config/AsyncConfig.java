package com.beacon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Background threads for scans and the 6-hourly schedule; shutdown waits for in-flight scans. */
@Configuration
public class AsyncConfig {

  private static final int SCAN_THREADS = 2;
  private static final int SHUTDOWN_WAIT_SECONDS = 60;

  @Bean
  public ThreadPoolTaskExecutor scanExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(SCAN_THREADS);
    executor.setMaxPoolSize(SCAN_THREADS);
    executor.setThreadNamePrefix("scan-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
    return executor;
  }

  @Bean
  public ThreadPoolTaskScheduler snapshotTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("schedule-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
    return scheduler;
  }
}

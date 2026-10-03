package com.beacon.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beacon.snapshot.SnapshotService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class JobServiceTest {

  @Test
  void start_twiceWhileRunning_joinsSameJob() throws Exception {
    JobRepository repo = mock(JobRepository.class);
    when(repo.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
    SnapshotService snapshots = mock(SnapshotService.class);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    doAnswer(
            inv -> {
              started.countDown();
              release.await(5, TimeUnit.SECONDS);
              return null;
            })
        .when(snapshots)
        .capture(anyLong(), any(UUID.class));
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.initialize();
    JobService service = new JobService(repo, new StoreLockRegistry(), snapshots, executor);

    JobService.StartedJob first = service.start(1L, JobType.REFRESH);
    when(repo.findById(first.job().getId())).thenReturn(Optional.of(first.job()));
    started.await(5, TimeUnit.SECONDS);
    JobService.StartedJob second = service.start(1L, JobType.REFRESH);
    release.countDown();
    executor.shutdown();

    assertThat(first.joinedExisting()).isFalse();
    assertThat(second.joinedExisting()).isTrue();
    assertThat(second.job().getId()).isEqualTo(first.job().getId());
    verify(snapshots, times(1)).capture(anyLong(), any(UUID.class));
  }
}

package com.beacon.web;

import com.beacon.config.BeaconProperties;
import com.beacon.job.JobService;
import com.beacon.snapshot.Snapshot;
import com.beacon.snapshot.SnapshotRepository;
import com.beacon.snapshot.SnapshotStatus;
import com.beacon.store.Store;
import com.beacon.store.StoreService;
import com.beacon.web.dto.AddStoreRequest;
import com.beacon.web.dto.AddStoreResponse;
import com.beacon.web.dto.RefreshResponse;
import com.beacon.web.dto.StoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
@Tag(name = "Stores", description = "Add stores and start scans")
public class StoreController {

  private static final Logger log = LoggerFactory.getLogger(StoreController.class);

  private final StoreService stores;
  private final SnapshotRepository snapshots;
  private final Duration interval;
  private final boolean schedulerEnabled;

  public StoreController(
      StoreService stores, SnapshotRepository snapshots, BeaconProperties properties) {
    this.stores = stores;
    this.snapshots = snapshots;
    this.interval = properties.snapshot().interval();
    // Demo mode replays recordings, so there is no "next check" to promise
    this.schedulerEnabled = properties.snapshot().schedulerEnabled() && !properties.demo();
  }

  @GetMapping
  @Operation(summary = "List tracked stores")
  public List<StoreResponse> list() {
    return stores.list().stream().map(this::toResponse).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Add a store and start its first scan (runs in the background)")
  public AddStoreResponse add(@Valid @RequestBody AddStoreRequest request) {
    log.info("[API] POST /api/stores url={}", request.url());
    StoreService.Added added = stores.add(request.url());
    return new AddStoreResponse(
        added.store().getId(), added.job().job().getId(), added.job().job().getStatus().name());
  }

  @GetMapping("/{storeId}")
  @Operation(summary = "Store details and current snapshot")
  public StoreResponse get(@PathVariable long storeId) {
    return toResponse(stores.get(storeId));
  }

  @PostMapping("/{storeId}/snapshot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Refresh now (joins a scan that is already running)")
  public RefreshResponse refresh(@PathVariable long storeId) {
    JobService.StartedJob job = stores.refresh(storeId);
    return new RefreshResponse(job.job().getId(), job.joinedExisting());
  }

  private StoreResponse toResponse(Store s) {
    Optional<Snapshot> current =
        s.getCurrentSnapshotId() == null
            ? Optional.empty()
            : snapshots.findById(s.getCurrentSnapshotId());
    Instant lastChecked = current.map(Snapshot::getStartedAt).orElse(null);
    Optional<Snapshot> latest = snapshots.findTopByStoreIdOrderByStartedAtDesc(s.getId());
    StoreResponse.LastFailure failure =
        latest
            .filter(x -> x.getStatus() == SnapshotStatus.FAILED)
            .map(
                x ->
                    new StoreResponse.LastFailure(
                        x.getErrorCode(), x.getErrorMessage(), x.getFinishedAt()))
            .orElse(null);
    return new StoreResponse(
        s.getId(),
        s.getDomain(),
        s.getDisplayName(),
        s.getPlatform().name(),
        s.getCurrency(),
        s.getStatus().name(),
        s.getCurrentSnapshotId(),
        lastChecked,
        lastChecked == null || !schedulerEnabled ? null : lastChecked.plus(interval),
        failure);
  }
}

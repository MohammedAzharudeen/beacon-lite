package com.beacon.web.dto;

import java.time.Instant;
import java.util.List;

public record HealthResponse(String status, List<StoreHealth> stores, boolean llmReachable) {

  public record StoreHealth(long storeId, String domain, Instant lastRun, String lastStatus) {}
}

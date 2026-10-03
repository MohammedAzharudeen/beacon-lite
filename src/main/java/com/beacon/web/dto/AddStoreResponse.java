package com.beacon.web.dto;

import java.util.UUID;

public record AddStoreResponse(Long storeId, UUID jobId, String status) {}

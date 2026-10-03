package com.beacon.web.dto;

import java.util.UUID;

public record RefreshResponse(UUID jobId, boolean joinedExisting) {}

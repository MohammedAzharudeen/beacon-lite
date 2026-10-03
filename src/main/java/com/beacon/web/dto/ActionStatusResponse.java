package com.beacon.web.dto;

import java.time.Instant;

public record ActionStatusResponse(String actionKey, String status, Instant updatedAt) {}

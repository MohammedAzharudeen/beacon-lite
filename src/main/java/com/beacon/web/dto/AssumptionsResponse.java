package com.beacon.web.dto;

import com.beacon.config.Assumptions;

public record AssumptionsResponse(String version, Assumptions values) {}

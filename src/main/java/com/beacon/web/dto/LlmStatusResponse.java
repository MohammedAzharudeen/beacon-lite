package com.beacon.web.dto;

public record LlmStatusResponse(String provider, String baseUrl, String model, boolean reachable) {}

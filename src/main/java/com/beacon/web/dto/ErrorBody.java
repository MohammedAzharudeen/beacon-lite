package com.beacon.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Error format for every API error. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorBody(
    Instant timestamp,
    int status,
    String code,
    String message,
    String hint,
    String path,
    Map<String, Object> details) {}

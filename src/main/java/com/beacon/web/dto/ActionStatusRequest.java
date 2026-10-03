package com.beacon.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ActionStatusRequest(
    @NotNull(message = "status is required")
        @Pattern(regexp = "TODO|DONE|DISMISSED", message = "Invalid status")
        String status) {}

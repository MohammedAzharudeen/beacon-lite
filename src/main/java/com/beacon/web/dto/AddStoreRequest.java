package com.beacon.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddStoreRequest(
    @NotBlank(message = "URL is required") @Size(max = 2048, message = "URL is too long")
        String url) {}

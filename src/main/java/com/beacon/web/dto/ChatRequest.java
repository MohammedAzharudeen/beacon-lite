package com.beacon.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(
    @NotNull(message = "storeId is required") Long storeId,
    @NotBlank(message = "Message is required") @Size(max = 1000, message = "Message is too long")
        String message,
    @Size(max = 20, message = "Too many history turns") List<@Valid ChatTurn> history) {

  public record ChatTurn(
      @Pattern(regexp = "user|assistant", message = "role must be user or assistant") String role,
      @Size(max = 4000, message = "Turn is too long") String content) {}
}

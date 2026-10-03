package com.beacon.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public record JobResponse(
    UUID jobId,
    Long storeId,
    String type,
    String status,
    String step,
    int productsRead,
    Integer productsEstimate,
    @JsonInclude(JsonInclude.Include.NON_NULL) JobError error) {

  /** Why a job failed, with what to do next. */
  public record JobError(String code, String message, String hint) {}
}

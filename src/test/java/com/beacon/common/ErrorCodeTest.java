package com.beacon.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void everyCode_hasMessageAndHint() {
    for (ErrorCode code : ErrorCode.values()) {
      assertThat(code.defaultMessage()).as(code.name()).isNotBlank();
      assertThat(code.defaultHint()).as(code.name()).isNotBlank();
      assertThat(code.httpStatus()).as(code.name()).isNotNull();
    }
  }

  @Test
  void exception_carriesCodeHintAndDetails() {
    BeaconException e =
        new BeaconException(ErrorCode.STORE_ALREADY_TRACKED, java.util.Map.of("storeId", 1L));

    assertThat(e.code()).isEqualTo(ErrorCode.STORE_ALREADY_TRACKED);
    assertThat(e.getMessage()).isEqualTo("This store is already tracked");
    assertThat(e.hint()).isEqualTo("Opening the existing store");
    assertThat(e.details()).containsEntry("storeId", 1L);
  }
}

package com.beacon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void of_keepsTwoDecimalsAndCurrency() {
    Money price = Money.of("79.99", "USD");

    assertThat(price.amount()).isEqualByComparingTo("79.99");
    assertThat(price.currency()).isEqualTo("USD");
  }

  @Test
  void times_roundsHalfUpToCents() {
    Money price = Money.of("79.99", "USD");

    assertThat(price.times(new BigDecimal("0.333")).amount()).isEqualByComparingTo("26.64");
  }

  @Test
  void currency_canBeUnknown() {
    assertThat(Money.of("10", null).currency()).isNull();
  }
}

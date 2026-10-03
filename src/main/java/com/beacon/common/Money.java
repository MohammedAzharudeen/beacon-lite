package com.beacon.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in the store's currency. The currency is {@code null} when the store doesn't expose it,
 * and the UI then shows "Currency unknown" instead of assuming dollars.
 *
 * @param amount the value, kept at two decimal places
 * @param currency ISO 4217 code such as {@code USD}, or {@code null} when unknown
 */
public record Money(BigDecimal amount, String currency) {

  public Money {
    Objects.requireNonNull(amount, "amount");
    amount = amount.setScale(2, RoundingMode.HALF_UP);
  }

  public static Money of(String amount, String currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public Money times(BigDecimal factor) {
    return new Money(amount.multiply(factor), currency);
  }
}

package com.beacon.insight.report;

import com.beacon.common.Money;

/** A money figure that is an estimate; always shown with the Estimate badge. */
public record EstimatedMoney(String amount, String currency, boolean estimate) {

  public static EstimatedMoney of(Money money) {
    return new EstimatedMoney(money.amount().toPlainString(), money.currency(), true);
  }
}

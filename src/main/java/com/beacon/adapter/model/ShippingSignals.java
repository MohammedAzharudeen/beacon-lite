package com.beacon.adapter.model;

import java.math.BigDecimal;

/**
 * Free-shipping offer found in store text.
 *
 * @param text the sentence found, or {@code null}
 * @param threshold order value for free shipping, or {@code null} when no amount is stated
 * @param source page the text came from
 */
public record ShippingSignals(
    SourceStatus status, String text, BigDecimal threshold, String source) {}

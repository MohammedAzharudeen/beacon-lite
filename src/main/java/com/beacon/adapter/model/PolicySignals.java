package com.beacon.adapter.model;

/**
 * Returns policy as read from the policy page or a footer page.
 *
 * @param path page the values came from
 * @param robotsRule the rule that blocked the usual page, when an alternative was used
 * @param returnDays returns window in days, or {@code null} when not stated
 * @param freeReturns true when the page says returns are free; {@code null} when not stated
 */
public record PolicySignals(
    SourceStatus status, String path, String robotsRule, Integer returnDays, Boolean freeReturns) {}

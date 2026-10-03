package com.beacon.insight.report;

import com.beacon.action.ActionState;

/**
 * A recommended action in the top 5.
 *
 * @param actionKey stable key, e.g. {@code RESTOCK:product:7307477418117}
 * @param category badge such as Restock, Catalog, Discovery
 * @param atRiskPerWeek only for restock actions
 */
public record Action(
    String actionKey,
    int rank,
    String title,
    String evidence,
    String category,
    EstimatedMoney atRiskPerWeek,
    ActionState status) {

  public Action withRankAndStatus(int newRank, ActionState newStatus) {
    return new Action(actionKey, newRank, title, evidence, category, atRiskPerWeek, newStatus);
  }
}

package com.beacon.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** Merchant-set status of a recommended action (DEVSPEC 4.8). */
@Entity
@Table(name = "action_status")
public class ActionStatus {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "action_key", nullable = false)
  private String actionKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActionState status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ActionStatus() {}

  public static ActionStatus of(Long storeId, String actionKey, ActionState status) {
    ActionStatus actionStatus = new ActionStatus();
    actionStatus.storeId = storeId;
    actionStatus.actionKey = actionKey;
    actionStatus.status = status;
    return actionStatus;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public void change(ActionState newStatus) {
    this.status = newStatus;
  }

  public Long getId() {
    return id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public String getActionKey() {
    return actionKey;
  }

  public ActionState getStatus() {
    return status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

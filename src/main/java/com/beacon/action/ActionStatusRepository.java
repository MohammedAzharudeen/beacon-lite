package com.beacon.action;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionStatusRepository extends JpaRepository<ActionStatus, Long> {

  List<ActionStatus> findByStoreId(Long storeId);

  Optional<ActionStatus> findByStoreIdAndActionKey(Long storeId, String actionKey);
}

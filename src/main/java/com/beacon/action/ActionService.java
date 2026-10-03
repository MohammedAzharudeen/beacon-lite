package com.beacon.action;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.store.StoreRepository;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Merchant-set action statuses (To do / Done / Dismissed), kept per store across restarts. */
@Service
public class ActionService {

  private static final Logger log = LoggerFactory.getLogger(ActionService.class);

  private final ActionStatusRepository repository;
  private final StoreRepository stores;

  public ActionService(ActionStatusRepository repository, StoreRepository stores) {
    this.repository = repository;
    this.stores = stores;
  }

  /** Statuses of a store's actions, by action key. */
  @Transactional(readOnly = true)
  public Map<String, ActionState> states(long storeId) {
    Map<String, ActionState> out = new HashMap<>();
    repository.findByStoreId(storeId).forEach(a -> out.put(a.getActionKey(), a.getStatus()));
    return out;
  }

  /** Sets an action's status; DISMISSED actions stop appearing in the top 5. */
  @Transactional
  public ActionStatus set(long storeId, String actionKey, ActionState state) {
    if (!stores.existsById(storeId)) {
      throw new BeaconException(ErrorCode.NOT_FOUND);
    }
    log.info("[ACTION] store={} key={} status={}", storeId, actionKey, state);
    ActionStatus row =
        repository
            .findByStoreIdAndActionKey(storeId, actionKey)
            .orElseGet(() -> ActionStatus.of(storeId, actionKey, state));
    row.change(state);
    return repository.save(row);
  }
}

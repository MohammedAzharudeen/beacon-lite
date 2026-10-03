package com.beacon.diff;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeEventRepository extends JpaRepository<ChangeEvent, Long> {

  List<ChangeEvent> findByStoreIdOrderByWindowEndDesc(Long storeId, Pageable page);

  List<ChangeEvent> findByStoreIdAndToSnapshotId(Long storeId, Long toSnapshotId);
}

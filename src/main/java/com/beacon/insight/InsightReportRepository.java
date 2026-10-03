package com.beacon.insight;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsightReportRepository extends JpaRepository<InsightReportEntity, Long> {

  Optional<InsightReportEntity> findTopByStoreIdOrderByGeneratedAtDesc(Long storeId);

  /** Oldest first; feeds the KPI trend lines. */
  List<InsightReportEntity> findByStoreIdOrderByGeneratedAtAsc(Long storeId);
}

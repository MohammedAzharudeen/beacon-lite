package com.beacon.job;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, UUID> {

  Optional<Job> findFirstByStoreIdAndStatusIn(Long storeId, Collection<JobStatus> statuses);
}

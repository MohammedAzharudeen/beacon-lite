package com.beacon.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<Variant, Long> {

  Optional<Variant> findByStoreIdAndExternalId(Long storeId, Long externalId);

  List<Variant> findByStoreId(Long storeId);

  List<Variant> findByProductIdIn(Collection<Long> productIds);
}

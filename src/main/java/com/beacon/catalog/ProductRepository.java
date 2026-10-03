package com.beacon.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> findByStoreIdAndExternalId(Long storeId, Long externalId);

  List<Product> findByStoreId(Long storeId);

  List<Product> findByStoreIdAndRemovedFalse(Long storeId);
}

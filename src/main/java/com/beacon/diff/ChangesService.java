package com.beacon.diff;

import com.beacon.catalog.Product;
import com.beacon.catalog.ProductRepository;
import com.beacon.catalog.Variant;
import com.beacon.catalog.VariantRepository;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Change events with product names and sizes, newest window first. */
@Service
public class ChangesService {

  private static final int MAX_LIMIT = 500;

  private final ChangeEventRepository events;
  private final ProductRepository products;
  private final VariantRepository variants;
  private final StoreRepository stores;

  public ChangesService(
      ChangeEventRepository events,
      ProductRepository products,
      VariantRepository variants,
      StoreRepository stores) {
    this.events = events;
    this.products = products;
    this.variants = variants;
    this.stores = stores;
  }

  /** Changes and the distinct snapshot windows they fall in. */
  public record Changes(List<ChangeView> events, List<Window> windows) {}

  /** One snapshot-to-snapshot window. */
  public record Window(Instant start, Instant end) {}

  @Transactional(readOnly = true)
  public Changes recent(long storeId, ChangeType type, int limit) {
    int size = Math.max(1, Math.min(limit, MAX_LIMIT));
    List<ChangeEvent> rows =
        events.findByStoreIdOrderByWindowEndDesc(
            storeId, PageRequest.of(0, type == null ? size : MAX_LIMIT));
    if (type != null) {
      rows = rows.stream().filter(e -> e.getType() == type).limit(size).toList();
    }
    Map<Long, Product> productById = new HashMap<>();
    products
        .findAllById(rows.stream().map(ChangeEvent::getProductId).filter(Objects::nonNull).toList())
        .forEach(p -> productById.put(p.getId(), p));
    Map<Long, Variant> variantById = new HashMap<>();
    variants
        .findAllById(rows.stream().map(ChangeEvent::getVariantId).filter(Objects::nonNull).toList())
        .forEach(v -> variantById.put(v.getId(), v));
    String domain = stores.findById(storeId).map(Store::getDomain).orElse("");
    Set<Window> windows = new LinkedHashSet<>();
    List<ChangeView> views =
        rows.stream()
            .map(
                e -> {
                  Product p = e.getProductId() == null ? null : productById.get(e.getProductId());
                  Variant v = e.getVariantId() == null ? null : variantById.get(e.getVariantId());
                  windows.add(new Window(e.getWindowStart(), e.getWindowEnd()));
                  return new ChangeView(
                      e.getId(),
                      e.getType(),
                      p == null ? null : p.getExternalId(),
                      p == null ? null : p.getTitle(),
                      p == null ? null : "https://" + domain + "/products/" + p.getHandle(),
                      v == null ? null : v.getSizeLabel(),
                      e.getOldValue(),
                      e.getNewValue(),
                      e.getWindowStart(),
                      e.getWindowEnd());
                })
            .toList();
    return new Changes(views, List.copyOf(windows));
  }
}

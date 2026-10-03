package com.beacon.size;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A product's sizes, sorted, with core sizes marked.
 *
 * @param status whether the product has sizes and whether they were recognised
 * @param sizes sorted sizes (empty unless {@code status == ANALYSED})
 * @param weighted true when core sizes count more (fit sizes); false for volumes, sets, bedding,
 *     one size and multi-dimension sizes
 */
public record SizeRun(Status status, List<SizeEntry> sizes, boolean weighted) {

  public SizeRun {
    sizes = List.copyOf(sizes);
  }

  public enum Status {
    /** Sizes found and recognised. */
    ANALYSED,
    /** The product has no size option (e.g. memberships); skipped from size analysis. */
    NO_SIZE_OPTION,
    /** Some size values weren't recognised; listed as "size format not recognised". */
    UNRECOGNISED
  }

  public static SizeRun noSizes() {
    return new SizeRun(Status.NO_SIZE_OPTION, List.of(), false);
  }

  public static SizeRun unrecognised() {
    return new SizeRun(Status.UNRECOGNISED, List.of(), false);
  }

  public Optional<SizeEntry> entry(String label) {
    return sizes.stream().filter(s -> s.label().equals(label)).findFirst();
  }

  /** Sizes keyed by label, for variant lookups. */
  public Map<String, SizeEntry> byLabel() {
    Map<String, SizeEntry> map = new HashMap<>();
    sizes.forEach(s -> map.put(s.label(), s));
    return map;
  }

  public boolean hasOnlyOneSize() {
    return sizes.size() == 1 || sizes.stream().allMatch(s -> s.kind() == SizeKind.ONE_SIZE);
  }
}

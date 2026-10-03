package com.beacon.adapter.model;

import java.util.List;

/** A product option such as Color or Size, with its values in the store's order. */
public record OptionData(String name, int position, List<String> values) {

  public OptionData {
    values = List.copyOf(values);
  }
}

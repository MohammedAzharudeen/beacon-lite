package com.beacon.web.dto;

import java.util.List;

/** One page of a long list. */
public record Page<T>(List<T> rows, int total, String currency) {

  public Page {
    rows = List.copyOf(rows);
  }
}

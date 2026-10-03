package com.beacon.size;

/** Recognised families of size values. */
public enum SizeKind {
  /** XXS … 6XL, including words such as SMALL or XLRG. */
  LETTER,
  /** Shoe, dress, waist or kids sizes such as 7, 10.5, SZ 4, 1.0C. */
  NUMERIC,
  /** Wide-fit shoe sizes such as 8.0W. */
  WIDE,
  /** Unisex dual sizes such as "M 7.5 / W 9" (sorted by the men's size). */
  DUAL,
  /** A single size for everyone; no size gap is possible. */
  ONE_SIZE,
  /** Paired sizes such as S/M, 9-11 or 30/32. */
  COMBO,
  /** Volumes or weights such as 0.5 oz / 15 ml. */
  VOLUME,
  /** Sets or packs such as "3 piece set". */
  SET,
  /** Bed sizes such as Twin or Full/Queen. */
  BEDDING,
  /** Two or more size options (e.g. band × cup); each combination counts as one size. */
  MULTI,
  /** Not recognised; the product is listed as "size format not recognised", never guessed. */
  UNKNOWN;

  /** Fit sizes have a core range; other kinds use an unweighted share sold out. */
  public boolean isFitSize() {
    return this == LETTER || this == NUMERIC || this == WIDE || this == DUAL || this == COMBO;
  }
}

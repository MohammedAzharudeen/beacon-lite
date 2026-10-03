package com.beacon.size;

/**
 * One size value, classified.
 *
 * @param runKey sizes with the same key form one size run (e.g. regular and wide are separate)
 * @param sortKey position within the run
 */
public record ParsedSize(String label, SizeKind kind, String runKey, double sortKey) {}

package com.beacon.common;

import org.springframework.http.HttpStatus;

/**
 * Every error the API or a job can report (DEVSPEC Section 9.1). Each carries the HTTP status used
 * when it reaches a client, a user-facing message and a hint telling the user what to do next.
 */
public enum ErrorCode {
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "Validation failed", "Check the highlighted fields"),
  INVALID_URL(
      HttpStatus.BAD_REQUEST,
      "That doesn't look like a web address",
      "Enter a store address such as stevemadden.com"),
  URL_NOT_ALLOWED(
      HttpStatus.BAD_REQUEST,
      "This address can't be scanned",
      "Use a public store address such as stevemadden.com"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Not found", "Check the link or pick a store from the list"),
  STORE_ALREADY_TRACKED(
      HttpStatus.CONFLICT, "This store is already tracked", "Opening the existing store"),
  REPORT_NOT_READY(
      HttpStatus.CONFLICT, "First scan still running", "Results appear when the scan finishes"),
  PLATFORM_NOT_SUPPORTED(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "We couldn't read a product catalog from this store",
      "Beacon Lite fully supports standard Shopify stores"),
  CATALOG_FEED_UNAVAILABLE(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "This Shopify store uses a custom storefront that doesn't publish its catalog",
      "Try a standard Shopify store such as stevemadden.com"),
  STORE_NOT_REACHABLE(
      HttpStatus.BAD_GATEWAY, "The store didn't respond", "Check the address and try again later"),
  STORE_RATE_LIMITED(
      HttpStatus.SERVICE_UNAVAILABLE,
      "The store asked us to slow down",
      "Try again in a few minutes"),
  ROBOTS_UNAVAILABLE(
      HttpStatus.BAD_GATEWAY,
      "The store's robots.txt couldn't be read",
      "Nothing was fetched; try again later"),
  SNAPSHOT_WRITE_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "The scan couldn't be saved",
      "Check free disk space and try again"),
  PARSE_FAILED(
      HttpStatus.BAD_GATEWAY,
      "The store returned data we couldn't read",
      "Try again later; details are in the logs"),
  INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Something went wrong",
      "Try again; details are in the logs");

  private final HttpStatus httpStatus;
  private final String defaultMessage;
  private final String defaultHint;

  ErrorCode(HttpStatus httpStatus, String defaultMessage, String defaultHint) {
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
    this.defaultHint = defaultHint;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }

  public String defaultMessage() {
    return defaultMessage;
  }

  public String defaultHint() {
    return defaultHint;
  }
}

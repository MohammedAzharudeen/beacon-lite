package com.beacon.web;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.web.dto.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every error into the {code, message, hint} format; stack traces stay in the logs. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(BeaconException.class)
  public ResponseEntity<ErrorBody> beacon(BeaconException e, HttpServletRequest request) {
    if (e.code() == ErrorCode.INTERNAL_ERROR) {
      log.error("[API] path={} internal error", request.getRequestURI(), e);
      return body(
          ErrorCode.INTERNAL_ERROR,
          ErrorCode.INTERNAL_ERROR.defaultMessage(),
          ErrorCode.INTERNAL_ERROR.defaultHint(),
          request,
          Map.of());
    }
    log.info("[API] path={} code={}", request.getRequestURI(), e.code());
    return body(e.code(), e.getMessage(), e.hint(), request, e.details());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorBody> invalid(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    List<Map<String, String>> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList();
    return body(
        ErrorCode.INVALID_INPUT,
        ErrorCode.INVALID_INPUT.defaultMessage(),
        ErrorCode.INVALID_INPUT.defaultHint(),
        request,
        Map.of("errors", errors));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    ConstraintViolationException.class
  })
  public ResponseEntity<ErrorBody> unreadable(Exception e, HttpServletRequest request) {
    return body(
        ErrorCode.INVALID_INPUT,
        ErrorCode.INVALID_INPUT.defaultMessage(),
        "Check the request: " + e.getClass().getSimpleName().replace("Exception", ""),
        request,
        Map.of());
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
  public ResponseEntity<ErrorBody> notFound(Exception e, HttpServletRequest request) {
    return body(
        ErrorCode.NOT_FOUND,
        ErrorCode.NOT_FOUND.defaultMessage(),
        ErrorCode.NOT_FOUND.defaultHint(),
        request,
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest request) {
    log.error("[API] path={} unexpected", request.getRequestURI(), e);
    return body(
        ErrorCode.INTERNAL_ERROR,
        ErrorCode.INTERNAL_ERROR.defaultMessage(),
        ErrorCode.INTERNAL_ERROR.defaultHint(),
        request,
        Map.of());
  }

  private ResponseEntity<ErrorBody> body(
      ErrorCode code,
      String message,
      String hint,
      HttpServletRequest request,
      Map<String, Object> details) {
    return ResponseEntity.status(code.httpStatus())
        .body(
            new ErrorBody(
                clock.instant(),
                code.httpStatus().value(),
                code.name(),
                message,
                hint,
                request.getRequestURI(),
                details));
  }
}

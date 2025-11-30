package com.github.spud.tinystore.order.interfaces.error;

import com.github.spud.tinystore.order.domain.exception.OrderDomainException;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

  private final OrderMetrics metrics;

  public GlobalExceptionHandler(org.springframework.beans.factory.ObjectProvider<OrderMetrics> metricsProvider) {
    this.metrics = metricsProvider.getIfAvailable(() -> null);
  }

  @ExceptionHandler(OrderDomainException.class)
  public ResponseEntity<Map<String, Object>> handleOrderDomainException(OrderDomainException ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody(normalizeCode(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
    increment(HttpStatus.BAD_REQUEST);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    String msg = ex.getBindingResult().getAllErrors().stream()
        .findFirst().map(e -> e.getDefaultMessage()).orElse("Validation failed");
    Map<String, Object> body = errorBody("ORDER-4000", msg, request.getRequestURI());
    increment(HttpStatus.BAD_REQUEST);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody(normalizeCode(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
    increment(HttpStatus.UNAUTHORIZED);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody(normalizeCode(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
    increment(HttpStatus.FORBIDDEN);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody(normalizeCode(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
    increment(HttpStatus.NOT_FOUND);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(DomainConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(DomainConflictException ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody(normalizeCode(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
    increment(HttpStatus.CONFLICT);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(UnprocessableCommandException.class)
  public ResponseEntity<Map<String, Object>> handleUnprocessable(UnprocessableCommandException ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody(normalizeCode(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
    increment(HttpStatus.UNPROCESSABLE_ENTITY);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
    Map<String, Object> body = errorBody("ORDER-5000", "Internal server error", request.getRequestURI());
    increment(HttpStatus.INTERNAL_SERVER_ERROR);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  private static Map<String, Object> errorBody(String code, String message, String path) {
    Map<String, Object> body = new HashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("errorCode", code);
    body.put("message", message);
    body.put("path", path);
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      body.put("traceId", traceId);
    }
    return body;
  }

  private static String normalizeCode(String raw) {
    if (raw == null || raw.isBlank()) return "ORDER-UNKNOWN";
    return raw.startsWith("ORDER-") ? raw : ("ORDER-" + raw);
  }

  private void increment(HttpStatus status) {
    if (metrics != null) {
      try { metrics.incrementHttpError(status.value()); } catch (Exception ignored) { }
    }
  }
}

package com.github.spud.tinystore.order.interfaces.error;

import com.github.spud.tinystore.order.domain.exception.OrderDomainException;
import java.time.OffsetDateTime;
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

  @ExceptionHandler(OrderDomainException.class)
  public ResponseEntity<Map<String, Object>> handleOrderDomainException(OrderDomainException ex) {
    Map<String, Object> body = baseBody(ex.getMessage());
    body.put("code", normalizeCode(ex.getErrorCode()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String msg = ex.getBindingResult().getAllErrors().stream()
        .findFirst().map(e -> e.getDefaultMessage()).orElse("Validation failed");
    Map<String, Object> body = baseBody(msg);
    body.put("code", "ORDER-VALIDATION-ERROR");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
    Map<String, Object> body = baseBody(ex.getMessage());
    body.put("code", normalizeCode(ex.getErrorCode()));
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    Map<String, Object> body = baseBody("Internal server error");
    body.put("code", "ORDER-INTERNAL-ERROR");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  private static Map<String, Object> baseBody(String message) {
    Map<String, Object> body = new HashMap<>();
    body.put("message", message);
    body.put("timestamp", OffsetDateTime.now().toString());
    String traceId = MDC.get("traceId");
    if (traceId != null) body.put("traceId", traceId);
    return body;
  }

  private static String normalizeCode(String raw) {
    if (raw == null || raw.isBlank()) return "ORDER-UNKNOWN";
    return raw.startsWith("ORDER-") ? raw : ("ORDER-" + raw);
  }
}

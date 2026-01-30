package com.github.spud.tinystore.order.interfaces.error;

import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.exception.IdempotencyConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局异常处理器
 * 统一处理订单域内的异常并转换为标准 API 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理领域冲突异常（状态迁移非法、并发冲突等）
     */
    @ExceptionHandler(DomainConflictException.class)
    public ResponseEntity<Map<String, Object>> handleDomainConflictException(DomainConflictException ex) {
        log.warn("Domain conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "DOMAIN_CONFLICT", ex.getMessage());
    }

    /**
     * 处理幂等冲突异常
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflictException(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: key={}, message={}", ex.getIdempotencyKey(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFoundException(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    /**
     * 构建统一错误响应
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", status.value());
        error.put("errorCode", code);
        error.put("message", message);
        error.put("traceId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(error);
    }
}

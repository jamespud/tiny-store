package com.github.spud.tinystore.order.interfaces.error;

import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.exception.IdempotencyConflictException;
import com.github.spud.tinystore.order.interfaces.dto.response.OrderHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    public ResponseEntity<OrderHttpResponse<Object>> handleDomainConflictException(DomainConflictException ex) {
        log.warn("Domain conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(OrderHttpResponse.fail(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * 处理幂等冲突异常
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleIdempotencyConflictException(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: key={}, message={}", ex.getIdempotencyKey(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(OrderHttpResponse.fail(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleNotFoundException(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(OrderHttpResponse.fail(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(OrderHttpResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred"));
    }
}

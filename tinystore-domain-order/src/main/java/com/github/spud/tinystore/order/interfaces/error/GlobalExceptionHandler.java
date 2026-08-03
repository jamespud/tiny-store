package com.github.spud.tinystore.order.interfaces.error;

import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.exception.IdempotencyConflictException;
import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import com.github.spud.tinystore.order.interfaces.dto.response.OrderHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理订单域内的异常并转换为标准 API 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理幂等服务不可用异常（Redis 故障等基础设施问题）
     * 返回 503 Service Unavailable，通知客户端稍后重试
     */
    @ExceptionHandler(IdempotencyServiceUnavailableException.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleIdempotencyServiceUnavailableException(
            IdempotencyServiceUnavailableException ex) {
        log.error("[operation={}] [idempotencyKey={}] Idempotency service unavailable",
                ex.getOperation(), ex.getIdempotencyKey(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(OrderHttpResponse.fail(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Idempotency service unavailable, please retry later"));
    }

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
     * 处理 @Version 乐观锁冲突（回执/超时取消/支付回调并发更新 trade）
     * 返回 409 CONFLICT：状态已被并发修改，客户端应查询最新状态或重试
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(OrderHttpResponse.fail(HttpStatus.CONFLICT.value(),
                    "Concurrent modification, please retry or query latest state"));
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
     * 处理参数验证异常（Bean Validation）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(OrderHttpResponse.fail(HttpStatus.BAD_REQUEST.value(), "Validation failed: " + errorMessage));
    }

    /**
     * 处理 JSON 解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OrderHttpResponse<Object>> handleJsonParseException(HttpMessageNotReadableException ex) {
        log.warn("Invalid JSON request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(OrderHttpResponse.fail(HttpStatus.BAD_REQUEST.value(), "Invalid request format"));
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

package com.github.spud.tinystore.product.interfaces.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * GlobalExceptionHandler - Centralized exception handling for Product Service
 * 
 * Maps exceptions to appropriate HTTP status codes and standardized response format.
 * Generates trace IDs for troubleshooting and logs exceptions appropriately.
 * 
 * Response format:
 * {
 *   "code": "ERROR_CODE",
 *   "message": "Human-readable error message",
 *   "traceId": "unique-trace-id"
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle IllegalArgumentException - Invalid input data
     * Returns 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        String traceId = generateTraceId();
        log.warn("Invalid argument - traceId={}, message={}", traceId, e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "INVALID_ARGUMENT");
        response.put("message", e.getMessage());
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle IllegalStateException - Invalid operation for current state
     * Includes tenant-related errors (missing tenant context)
     * Returns 400 Bad Request
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        String traceId = generateTraceId();
        log.warn("Invalid state - traceId={}, message={}", traceId, e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "INVALID_STATE");
        response.put("message", e.getMessage());
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle validation errors from @Valid annotation
     * Returns 400 Bad Request with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException e) {
        String traceId = generateTraceId();
        
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        
        log.warn("Validation failed - traceId={}, errors={}", traceId, fieldErrors);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "VALIDATION_FAILED");
        response.put("message", "Input validation failed");
        response.put("traceId", traceId);
        response.put("fieldErrors", fieldErrors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle NoSuchElementException and EntityNotFoundException
     * Returns 404 Not Found
     */
    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception e) {
        String traceId = generateTraceId();
        log.warn("Resource not found - traceId={}, message={}", traceId, e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "NOT_FOUND");
        response.put("message", e.getMessage() != null ? e.getMessage() : "Resource not found");
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Handle OptimisticLockException - Concurrent modification detected
     * Returns 409 Conflict
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockException e) {
        String traceId = generateTraceId();
        log.warn("Optimistic lock failure - traceId={}, entity={}", traceId, e.getEntity());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "CONCURRENT_MODIFICATION");
        response.put("message", "Resource was modified by another transaction. Please retry.");
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handle DataIntegrityViolationException - Database constraint violation
     * Typically unique constraint or foreign key violations
     * Returns 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String traceId = generateTraceId();
        log.warn("Data integrity violation - traceId={}, message={}", traceId, e.getMessage());
        
        String message = "Data integrity constraint violated";
        
        // Try to extract meaningful message from exception
        if (e.getMessage() != null) {
            if (e.getMessage().contains("uk_product_spec")) {
                message = "SKU with this specification combination already exists";
            } else if (e.getMessage().contains("uk_tenant_rule_code")) {
                message = "Pricing rule with this code already exists";
            } else if (e.getMessage().contains("duplicate")) {
                message = "Duplicate entry detected";
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "CONSTRAINT_VIOLATION");
        response.put("message", message);
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handle AccessDeniedException - Insufficient permissions
     * Returns 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        String traceId = generateTraceId();
        log.warn("Access denied - traceId={}, message={}", traceId, e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "ACCESS_DENIED");
        response.put("message", "Insufficient permissions to perform this operation");
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * Handle all other exceptions - Internal server error
     * Returns 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        String traceId = generateTraceId();
        log.error("Unexpected error - traceId={}, type={}, message={}", 
                traceId, e.getClass().getSimpleName(), e.getMessage(), e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", "INTERNAL_ERROR");
        response.put("message", "An unexpected error occurred. Please contact support.");
        response.put("traceId", traceId);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Generate unique trace ID for request tracking
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}

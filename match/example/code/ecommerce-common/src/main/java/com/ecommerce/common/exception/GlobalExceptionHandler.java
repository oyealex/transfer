package com.ecommerce.common.exception;

import com.ecommerce.common.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler that translates domain exceptions into
 * the standard error response format for all controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        String traceId = generateTraceId();
        log.warn("Resource not found [{}]: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiError> handleAuthorization(AuthorizationException ex) {
        String traceId = generateTraceId();
        log.warn("Authorization failed [{}]: {}", traceId, ex.getMessage());
        HttpStatus status = AuthorizationException.CODE_UNAUTHORIZED.equals(ex.getCode())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitException ex) {
        String traceId = generateTraceId();
        log.warn("Rate limit exceeded [{}]: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        String traceId = generateTraceId();
        log.warn("Conflict [{}]: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ApiError> handleOrderValidation(OrderValidationException ex) {
        String traceId = generateTraceId();
        log.warn("Order validation failed [{}]: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException ex) {
        String traceId = generateTraceId();
        log.warn("Validation failed [{}]: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        String traceId = generateTraceId();
        log.warn("Business exception [{}]: code={}, message={}", traceId, ex.getCode(), ex.getMessage());
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), traceId, ex.getDetails());
        // USER_NOT_ACTIVE and USER_FROZEN should return 403 (Forbidden)
        if ("USER_NOT_ACTIVE".equals(ex.getCode()) || "USER_FROZEN".equals(ex.getCode())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        // REVIEW_PURCHASE_REQUIRED should also return 403
        if ("REVIEW_PURCHASE_REQUIRED".equals(ex.getCode())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String traceId = generateTraceId();
        Map<String, Object> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                details.put(fieldError.getField(), fieldError.getDefaultMessage()));

        log.warn("Validation failed [{}]: {}", traceId, details);
        ApiError error = new ApiError("VALIDATION_FAILED", "Request validation failed", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        String traceId = generateTraceId();
        log.error("Internal error [{}]: {}", traceId, ex.getMessage(), ex);
        ApiError error = new ApiError("INTERNAL_ERROR", "An unexpected error occurred", traceId, new HashMap<>());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8) + "-" + System.currentTimeMillis();
    }
}

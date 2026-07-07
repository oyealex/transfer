package com.ecommerce.common.exception;

/**
 * Thrown when request validation fails.
 * Corresponds to HTTP 400.
 */
public class ValidationException extends BusinessException {

    public static final String CODE = "VALIDATION_FAILED";

    public ValidationException(String message) {
        super(CODE, message);
    }

    public ValidationException(String field, String reason) {
        super(CODE, "Validation failed for field '" + field + "': " + reason);
    }
}

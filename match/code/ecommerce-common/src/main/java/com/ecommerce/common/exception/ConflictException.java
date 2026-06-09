package com.ecommerce.common.exception;

/**
 * Thrown when a conflict occurs, such as duplicate requests or state conflicts.
 * Corresponds to HTTP 409.
 */
public class ConflictException extends BusinessException {

    public static final String CODE = "CONFLICT";

    public ConflictException(String message) {
        super(CODE, message);
    }
}

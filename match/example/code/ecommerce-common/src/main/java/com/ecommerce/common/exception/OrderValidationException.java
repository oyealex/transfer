package com.ecommerce.common.exception;

/**
 * Thrown when order data fails domain validation.
 * Per the architecture spec, order amount validation must throw this,
 * never a plain java.lang.IllegalArgumentException.
 * Corresponds to HTTP 400.
 */
public class OrderValidationException extends BusinessException {

    public static final String CODE = "ORDER_INVALID_AMOUNT";

    public OrderValidationException(String message) {
        super(CODE, message);
    }

    public OrderValidationException(String code, String message) {
        super(code, message);
    }
}

package com.ecommerce.common.exception;

/**
 * Thrown when a rate limit is exceeded.
 * Corresponds to HTTP 429.
 */
public class RateLimitException extends BusinessException {

    public static final String CODE = "RATE_LIMITED";

    public RateLimitException(String message) {
        super(CODE, message);
    }
}

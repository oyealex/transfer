package com.ecommerce.common.exception;

/**
 * Thrown when authentication or authorization fails.
 * Corresponds to HTTP 401 (Unauthorized) or 403 (Forbidden).
 */
public class AuthorizationException extends BusinessException {

    public static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String CODE_FORBIDDEN = "FORBIDDEN";

    public AuthorizationException(String code, String message) {
        super(code, message);
    }

    public static AuthorizationException unauthorized(String message) {
        return new AuthorizationException(CODE_UNAUTHORIZED, message);
    }

    public static AuthorizationException forbidden(String message) {
        return new AuthorizationException(CODE_FORBIDDEN, message);
    }
}

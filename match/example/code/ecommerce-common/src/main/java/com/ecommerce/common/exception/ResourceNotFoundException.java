package com.ecommerce.common.exception;

/**
 * Thrown when a requested resource does not exist.
 * Corresponds to HTTP 404.
 */
public class ResourceNotFoundException extends BusinessException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(CODE, message);
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(CODE, resourceName + " not found: " + identifier);
    }
}

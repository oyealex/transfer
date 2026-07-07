package com.ecommerce.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Base business exception for the ShopHub e-commerce system.
 * All domain-specific exceptions should extend this class.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.details = new HashMap<>();
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = new HashMap<>();
    }

    public BusinessException addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}

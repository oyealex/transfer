package com.ecommerce.common.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Standard error response DTO returned by the global exception handler.
 */
public class ApiError {

    private String code;
    private String message;
    private String traceId;
    private Map<String, Object> details;

    public ApiError() {
    }

    public ApiError(String code, String message, String traceId, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.details = details != null ? details : new HashMap<>();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}

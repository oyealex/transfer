package com.ecommerce.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiError")
class ApiErrorTest {

    @Test
    @DisplayName("all-args constructor sets all fields including defaults for null details")
    void testAllArgsConstructor_setsAllFields() {
        Map<String, Object> details = new HashMap<>();
        details.put("field", "email");

        ApiError error = new ApiError("ERR_001", "Something went wrong", "trace-abc-123", details);

        assertThat(error.getCode()).isEqualTo("ERR_001");
        assertThat(error.getMessage()).isEqualTo("Something went wrong");
        assertThat(error.getTraceId()).isEqualTo("trace-abc-123");
        assertThat(error.getDetails()).containsEntry("field", "email");
    }

    @Test
    @DisplayName("all-args constructor replaces null details map with an empty HashMap")
    void testAllArgsConstructor_nullDetailsDefaultsToEmptyMap() {
        ApiError error = new ApiError("ERR_002", "Error message", "trace-xyz", null);

        assertThat(error.getDetails()).isNotNull();
        assertThat(error.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("no-args constructor creates empty object with all fields null")
    void testNoArgsConstructor_createsEmptyObject() {
        ApiError error = new ApiError();

        assertThat(error.getCode()).isNull();
        assertThat(error.getMessage()).isNull();
        assertThat(error.getTraceId()).isNull();
        assertThat(error.getDetails()).isNull();
    }

    @Test
    @DisplayName("setters allow overriding all fields including traceId")
    void testSetters_overrideAllFields() {
        ApiError error = new ApiError();
        Map<String, Object> details = new HashMap<>();
        details.put("reason", "test");

        error.setCode("UPDATED");
        error.setMessage("Updated message");
        error.setTraceId("trace-updated");
        error.setDetails(details);

        assertThat(error.getCode()).isEqualTo("UPDATED");
        assertThat(error.getMessage()).isEqualTo("Updated message");
        assertThat(error.getTraceId()).isEqualTo("trace-updated");
        assertThat(error.getDetails()).containsEntry("reason", "test");
    }

    @Test
    @DisplayName("traceId remains mutable via setter after construction")
    void testTraceId_isMutable() {
        ApiError error = new ApiError("CODE", "msg", "initial-trace", null);
        assertThat(error.getTraceId()).isEqualTo("initial-trace");

        error.setTraceId("new-trace-id");
        assertThat(error.getTraceId()).isEqualTo("new-trace-id");
    }
}

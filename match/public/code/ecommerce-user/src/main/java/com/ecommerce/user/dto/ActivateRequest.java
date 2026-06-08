package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for email activation using a one-time token.
 */
public class ActivateRequest {

    @NotBlank(message = "Activation token is required")
    private String token;

    public ActivateRequest() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}

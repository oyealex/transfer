package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for admin cancellation review.
 */
public class AdminCancelReviewRequest {

    @NotBlank(message = "Review decision is required (APPROVE or REJECT)")
    private String decision;

    private String comment;

    public AdminCancelReviewRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

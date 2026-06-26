package com.ecommerce.review.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for admin review approval/rejection.
 */
public class ReviewApprovalRequest {

    @NotNull
    private boolean approved;

    private String reviewerNote;

    public ReviewApprovalRequest() {
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public void setReviewerNote(String reviewerNote) {
        this.reviewerNote = reviewerNote;
    }
}

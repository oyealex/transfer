package com.ecommerce.payment.dto;

public class RefundReviewRequest {

    private boolean approved;
    private String note;

    public RefundReviewRequest() {
    }

    public RefundReviewRequest(boolean approved, String note) {
        this.approved = approved;
        this.note = note;
    }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

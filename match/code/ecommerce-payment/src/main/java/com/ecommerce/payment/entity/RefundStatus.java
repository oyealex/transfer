package com.ecommerce.payment.entity;

public enum RefundStatus {
    PENDING_REVIEW,
    APPROVED,
    WAITING_WAREHOUSE_ACCEPT,
    WAREHOUSE_ACCEPTED,
    COMPLETED,
    REJECTED
}

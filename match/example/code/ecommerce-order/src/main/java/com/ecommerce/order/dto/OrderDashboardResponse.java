package com.ecommerce.order.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for admin dashboard order summary.
 */
public class OrderDashboardResponse {

    private long pendingPayment;
    private long paid;
    private long processing;
    private long delivered;
    private long completed;
    private long cancelReviewing;
    private long cancelled;
    private long refunding;
    private long totalOrders;
    private LocalDateTime snapshotTime;

    public OrderDashboardResponse() {
    }

    public long getPendingPayment() { return pendingPayment; }
    public void setPendingPayment(long pendingPayment) { this.pendingPayment = pendingPayment; }

    public long getPaid() { return paid; }
    public void setPaid(long paid) { this.paid = paid; }

    public long getProcessing() { return processing; }
    public void setProcessing(long processing) { this.processing = processing; }

    public long getDelivered() { return delivered; }
    public void setDelivered(long delivered) { this.delivered = delivered; }

    public long getCompleted() { return completed; }
    public void setCompleted(long completed) { this.completed = completed; }

    public long getCancelReviewing() { return cancelReviewing; }
    public void setCancelReviewing(long cancelReviewing) { this.cancelReviewing = cancelReviewing; }

    public long getCancelled() { return cancelled; }
    public void setCancelled(long cancelled) { this.cancelled = cancelled; }

    public long getRefunding() { return refunding; }
    public void setRefunding(long refunding) { this.refunding = refunding; }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }

    public LocalDateTime getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(LocalDateTime snapshotTime) { this.snapshotTime = snapshotTime; }
}

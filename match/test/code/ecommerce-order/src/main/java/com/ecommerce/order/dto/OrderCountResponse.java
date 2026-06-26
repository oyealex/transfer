package com.ecommerce.order.dto;

/**
 * Response DTO for order count by category.
 */
public class OrderCountResponse {

    private int pendingPayment;
    private int processing;
    private int shipped;
    private int completed;
    private int cancelledOrRefunding;
    private int total;

    public OrderCountResponse() {
    }

    public int getPendingPayment() { return pendingPayment; }
    public void setPendingPayment(int pendingPayment) { this.pendingPayment = pendingPayment; }

    public int getProcessing() { return processing; }
    public void setProcessing(int processing) { this.processing = processing; }

    public int getShipped() { return shipped; }
    public void setShipped(int shipped) { this.shipped = shipped; }

    public int getCompleted() { return completed; }
    public void setCompleted(int completed) { this.completed = completed; }

    public int getCancelledOrRefunding() { return cancelledOrRefunding; }
    public void setCancelledOrRefunding(int cancelledOrRefunding) {
        this.cancelledOrRefunding = cancelledOrRefunding;
    }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}

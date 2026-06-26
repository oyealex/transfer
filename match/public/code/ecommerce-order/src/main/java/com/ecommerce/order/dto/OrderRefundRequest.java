package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for initiating an order refund.
 */
public class OrderRefundRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotBlank(message = "Refund reason is required")
    private String reason;

    private boolean returnGoods;
    private String returnLogisticsNo;
    private String remark;

    public OrderRefundRequest() {
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isReturnGoods() { return returnGoods; }
    public void setReturnGoods(boolean returnGoods) { this.returnGoods = returnGoods; }

    public String getReturnLogisticsNo() { return returnLogisticsNo; }
    public void setReturnLogisticsNo(String returnLogisticsNo) { this.returnLogisticsNo = returnLogisticsNo; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

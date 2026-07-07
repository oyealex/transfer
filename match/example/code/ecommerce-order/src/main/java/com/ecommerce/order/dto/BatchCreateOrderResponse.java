package com.ecommerce.order.dto;

import java.util.List;

/**
 * Response DTO for batch order creation.
 */
public class BatchCreateOrderResponse {

    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<BatchOrderResult> results;

    public BatchCreateOrderResponse() {
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<BatchOrderResult> getResults() {
        return results;
    }

    public void setResults(List<BatchOrderResult> results) {
        this.results = results;
    }

    /**
     * Individual result for one order in a batch.
     */
    public static class BatchOrderResult {

        private String externalOrderNo;
        private Long orderId;
        private String orderNo;
        private boolean success;
        private String error;

        public BatchOrderResult() {
        }

        public static BatchOrderResult success(String externalOrderNo, Long orderId, String orderNo) {
            BatchOrderResult r = new BatchOrderResult();
            r.externalOrderNo = externalOrderNo;
            r.orderId = orderId;
            r.orderNo = orderNo;
            r.success = true;
            return r;
        }

        public static BatchOrderResult failure(String externalOrderNo, String error) {
            BatchOrderResult r = new BatchOrderResult();
            r.externalOrderNo = externalOrderNo;
            r.success = false;
            r.error = error;
            return r;
        }

        public String getExternalOrderNo() {
            return externalOrderNo;
        }

        public void setExternalOrderNo(String externalOrderNo) {
            this.externalOrderNo = externalOrderNo;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}

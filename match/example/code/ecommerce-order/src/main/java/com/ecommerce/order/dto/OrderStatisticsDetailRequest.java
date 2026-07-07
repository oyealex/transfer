package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Extended statistics request with grouping options.
 */
public class OrderStatisticsDetailRequest {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private String groupBy;  // "day", "week", "month", "status", "user"
    private String statusFilter;
    private Long userId;

    public OrderStatisticsDetailRequest() {
    }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}

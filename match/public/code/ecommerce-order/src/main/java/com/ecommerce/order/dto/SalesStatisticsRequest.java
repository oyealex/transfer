package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for sales statistics queries.
 */
public class SalesStatisticsRequest {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    public SalesStatisticsRequest() {
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}

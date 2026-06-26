package com.ecommerce.loyalty.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for GET /api/v1/loyalty/points/history.
 */
public class PointsHistoryResponse {

    private Long id;
    private String type;
    private int amount;
    private int balance;
    private String description;
    private LocalDateTime createdAt;

    public PointsHistoryResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

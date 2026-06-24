package com.ecommerce.loyalty.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Records every points change (earn, redeem, expire, adjust) for audit
 * and history display.
 *
 * <p>The {@code id} and {@code createdAt} fields are inherited from
 * {@link BaseEntity}.
 */
@Entity
@Table(name = "points_transaction")
public class PointsTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointsTransactionType type;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int balance;

    @Column(name = "biz_type")
    private String bizType;

    @Column(name = "biz_id")
    private String bizId;

    @Column(length = 500)
    private String description;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // ---- constructors ----

    public PointsTransaction() {
    }

    // ---- getters and setters ----

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public PointsTransactionType getType() {
        return type;
    }

    public void setType(PointsTransactionType type) {
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

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}

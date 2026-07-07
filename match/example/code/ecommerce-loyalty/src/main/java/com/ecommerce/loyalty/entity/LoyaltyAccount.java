package com.ecommerce.loyalty.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Each user has exactly one LoyaltyAccount that tracks their points
 * balance, membership level, and annual consumption.
 *
 * <p>The {@code id} (primary key), {@code createdAt}, and {@code updatedAt}
 * fields are inherited from {@link BaseEntity}.
 */
@Entity
@Table(name = "loyalty_account")
public class LoyaltyAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "available_points", nullable = false)
    private int availablePoints;

    @Column(name = "frozen_points", nullable = false)
    private int frozenPoints;

    @Column(name = "redeemed_points", nullable = false)
    private int redeemedPoints;

    @Column(name = "expired_points", nullable = false)
    private int expiredPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_level", nullable = false)
    private MemberLevel memberLevel;

    @Column(name = "annual_consumption", precision = 14, scale = 2)
    private BigDecimal annualConsumption;

    // ---- constructors ----

    public LoyaltyAccount() {
    }

    // ---- getters and setters ----

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getAvailablePoints() {
        return availablePoints;
    }

    public void setAvailablePoints(int availablePoints) {
        this.availablePoints = availablePoints;
    }

    public int getFrozenPoints() {
        return frozenPoints;
    }

    public void setFrozenPoints(int frozenPoints) {
        this.frozenPoints = frozenPoints;
    }

    public int getRedeemedPoints() {
        return redeemedPoints;
    }

    public void setRedeemedPoints(int redeemedPoints) {
        this.redeemedPoints = redeemedPoints;
    }

    public int getExpiredPoints() {
        return expiredPoints;
    }

    public void setExpiredPoints(int expiredPoints) {
        this.expiredPoints = expiredPoints;
    }

    public MemberLevel getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(MemberLevel memberLevel) {
        this.memberLevel = memberLevel;
    }

    public BigDecimal getAnnualConsumption() {
        return annualConsumption;
    }

    public void setAnnualConsumption(BigDecimal annualConsumption) {
        this.annualConsumption = annualConsumption;
    }
}

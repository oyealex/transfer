package com.ecommerce.loyalty.dto;

import java.math.BigDecimal;

/**
 * Response DTO for GET /api/v1/loyalty/member-level.
 */
public class MemberLevelResponse {

    private String level;
    private String levelName;
    private double multiplier;
    private BigDecimal annualConsumption;
    private String nextLevelCondition;

    public MemberLevelResponse() {
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal getAnnualConsumption() {
        return annualConsumption;
    }

    public void setAnnualConsumption(BigDecimal annualConsumption) {
        this.annualConsumption = annualConsumption;
    }

    public String getNextLevelCondition() {
        return nextLevelCondition;
    }

    public void setNextLevelCondition(String nextLevelCondition) {
        this.nextLevelCondition = nextLevelCondition;
    }
}

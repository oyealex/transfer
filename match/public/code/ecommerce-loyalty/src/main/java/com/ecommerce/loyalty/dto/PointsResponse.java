package com.ecommerce.loyalty.dto;

/**
 * Response DTO for the GET /api/v1/loyalty/points endpoint.
 */
public class PointsResponse {

    private Long userId;
    private int totalPoints;
    private int availablePoints;
    private int frozenPoints;
    private String memberLevel;
    private String memberLevelName;

    public PointsResponse() {
    }

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

    public String getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(String memberLevel) {
        this.memberLevel = memberLevel;
    }

    public String getMemberLevelName() {
        return memberLevelName;
    }

    public void setMemberLevelName(String memberLevelName) {
        this.memberLevelName = memberLevelName;
    }
}

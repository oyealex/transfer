package com.ecommerce.review.dto;

import com.ecommerce.review.entity.ReviewStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a single review.
 */
public class ReviewResponse {

    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private int rating;
    private String content;
    private List<String> images;
    private ReviewStatus status;
    private boolean isAppended;
    private String reviewerResponse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reviewedAt;
    private List<ReviewAppendResponse> appends;

    public ReviewResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public boolean isAppended() {
        return isAppended;
    }

    public void setAppended(boolean isAppended) {
        this.isAppended = isAppended;
    }

    public String getReviewerResponse() {
        return reviewerResponse;
    }

    public void setReviewerResponse(String reviewerResponse) {
        this.reviewerResponse = reviewerResponse;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public List<ReviewAppendResponse> getAppends() {
        return appends;
    }

    public void setAppends(List<ReviewAppendResponse> appends) {
        this.appends = appends;
    }

    /**
     * Nested DTO for append data within a review response.
     */
    public static class ReviewAppendResponse {

        private Long id;
        private String content;
        private List<String> images;
        private LocalDateTime createdAt;

        public ReviewAppendResponse() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<String> getImages() {
            return images;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}

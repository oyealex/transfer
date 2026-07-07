package com.ecommerce.review.dto;

import com.ecommerce.common.dto.PageResponse;

/**
 * Response DTO for a paginated list of reviews.
 * Extends PageResponse to include standard pagination metadata.
 */
public class ReviewListResponse extends PageResponse<ReviewResponse> {

    private Double averageRating;
    private Long totalReviews;

    public ReviewListResponse() {
    }

    public ReviewListResponse(int page, int size, long total, java.util.List<ReviewResponse> items) {
        super(page, size, total, items);
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Long getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Long totalReviews) {
        this.totalReviews = totalReviews;
    }
}

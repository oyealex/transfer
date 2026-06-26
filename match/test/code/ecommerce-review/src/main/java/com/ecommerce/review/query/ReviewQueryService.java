package com.ecommerce.review.query;

import com.ecommerce.review.dto.ReviewResponse;

import java.util.List;

/**
 * Cross-module query interface exposed by the review module.
 * Other modules use this interface to query review data without
 * depending on review JPA entities or repositories.
 */
public interface ReviewQueryService {

    /**
     * Get all approved reviews for a product as DTOs.
     *
     * @param productId the product ID
     * @return list of review responses
     */
    List<ReviewResponse> getApprovedReviewsByProduct(Long productId);

    /**
     * Get a review by its ID, or throw ResourceNotFoundException.
     *
     * @param reviewId the review ID
     * @return the review response
     */
    ReviewResponse getReview(Long reviewId);
}

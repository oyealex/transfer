package com.ecommerce.review.controller;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.review.dto.ReviewAppendRequest;
import com.ecommerce.review.dto.ReviewCreateRequest;
import com.ecommerce.review.dto.ReviewListResponse;
import com.ecommerce.review.dto.ReviewResponse;
import com.ecommerce.review.service.ReviewService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review REST controller for user-facing endpoints.
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Create a new product review.
     * Requires USER role.
     *
     * @param request the review creation request
     * @return 201 Created with the review response
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody ReviewCreateRequest request) {
        Long userId = getCurrentUserId();
        log.info("POST /api/v1/reviews: userId={}, productId={}, rating={}",
                userId, request.getProductId(), request.getRating());
        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Append a follow-up comment to an existing approved review.
     * Requires USER role.
     *
     * @param reviewId the ID of the review to append to
     * @param request  the append request
     * @return 201 Created with the updated review response
     */
    @PostMapping("/{reviewId}/append")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReviewResponse> appendReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewAppendRequest request) {
        Long userId = getCurrentUserId();
        log.info("POST /api/v1/reviews/{}/append: userId={}", reviewId, userId);
        ReviewResponse response = reviewService.appendReview(userId, reviewId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get approved reviews for a product.
     * Anonymous access — anyone can view product reviews.
     *
     * @param productId the product ID
     * @param page      the page number (0-based, default 0)
     * @param size      the page size (default 10)
     * @return 200 OK with paginated reviews
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ReviewListResponse> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/v1/reviews/product/{}: page={}, size={}", productId, page, size);
        ReviewListResponse response = reviewService.getProductReviews(productId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get the current user's reviews.
     * Requires USER role.
     *
     * @param page the page number (0-based, default 0)
     * @param size the page size (default 10)
     * @return 200 OK with paginated reviews
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReviewListResponse> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        log.info("GET /api/v1/reviews/my: userId={}, page={}, size={}", userId, page, size);
        ReviewListResponse response = reviewService.getMyReviews(userId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Extracts the current user's ID from the Spring Security context.
     */
    private Long getCurrentUserId() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse user ID from principal '{}'", principal);
            throw new AuthorizationException(
                    "UNAUTHORIZED", "Invalid user principal: " + principal);
        }
    }
}

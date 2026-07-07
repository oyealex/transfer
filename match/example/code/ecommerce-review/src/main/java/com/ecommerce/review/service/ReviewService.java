package com.ecommerce.review.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.review.dto.ReviewAppendRequest;
import com.ecommerce.review.dto.ReviewCreateRequest;
import com.ecommerce.review.dto.ReviewListResponse;
import com.ecommerce.review.dto.ReviewResponse;
import com.ecommerce.review.entity.Review;
import com.ecommerce.review.entity.ReviewAppend;
import com.ecommerce.review.entity.ReviewStatus;
import com.ecommerce.review.repository.ReviewAppendRepository;
import com.ecommerce.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core service for review operations: create, append, and query reviews.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewAppendRepository reviewAppendRepository;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final DomainEventPublisher eventPublisher;
    private final OrderQueryService orderQueryService;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewAppendRepository reviewAppendRepository,
                         SensitiveWordFilter sensitiveWordFilter,
                         DomainEventPublisher eventPublisher,
                         OrderQueryService orderQueryService) {
        this.reviewRepository = reviewRepository;
        this.reviewAppendRepository = reviewAppendRepository;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.eventPublisher = eventPublisher;
        this.orderQueryService = orderQueryService;
    }

    /**
     * Create a new product review.
     *
     * @param userId  the ID of the user submitting the review
     * @param request the review creation request
     * @return the created review response
     */
    @Transactional
    public ReviewResponse createReview(Long userId, ReviewCreateRequest request) {
        // L4-03: Verify purchase — user must have purchased and received the product
        var purchaseVerification = orderQueryService.verifyPurchase(userId, request.getProductId());
        if (!purchaseVerification.isPurchased()) {
            throw new BusinessException("REVIEW_PURCHASE_REQUIRED",
                    "You must purchase and receive this product before reviewing");
        }

        if (request.getOrderItemId() == null) {
            throw new ValidationException("orderItemId", "orderItemId is required");
        }

        // Validate rating range
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException("INVALID_RATING",
                    "Rating must be between 1 and 5, got: " + request.getRating());
        }

        // Check for duplicate review on the same order item
        reviewRepository.findByUserIdAndOrderItemId(userId, request.getOrderItemId())
                .ifPresent(existing -> {
                    throw new BusinessException("DUPLICATE_REVIEW",
                            "You have already reviewed this order item");
                });

        // Filter sensitive words
        if (sensitiveWordFilter.containsSensitiveWord(request.getContent())) {
            throw new BusinessException("SENSITIVE_CONTENT",
                    "Your review contains prohibited content");
        }

        String filteredContent = sensitiveWordFilter.filter(request.getContent());

        // Build and save review entity
        Review review = new Review();
        review.setUserId(userId);
        review.setProductId(request.getProductId());
        review.setOrderId(request.getOrderId());
        review.setOrderItemId(request.getOrderItemId());
        review.setRating(request.getRating());
        review.setContent(filteredContent);
        review.setImages(imagesToJson(request.getImages()));
        review.setStatus(ReviewStatus.PENDING_REVIEW);
        review.setAppended(false);

        Review saved = reviewRepository.save(review);

        log.info("Review created: id={}, userId={}, productId={}, status=PENDING_REVIEW",
                saved.getId(), userId, request.getProductId());

        // L3-09: Reviews are NOT awarded points on submission.
        // Points are awarded only after admin approval via ReviewModerationService.
        // Do NOT publish ReviewApprovedEvent here.

        return toResponse(saved);
    }

    /**
     * Append a follow-up comment to an existing approved review.
     *
     * @param userId   the ID of the user appending
     * @param reviewId the ID of the original review
     * @param request  the append request
     * @return the updated review response
     */
    @Transactional
    public ReviewResponse appendReview(Long userId, Long reviewId, ReviewAppendRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        if (!review.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN",
                    "You can only append to your own reviews");
        }

        if (review.getStatus() != ReviewStatus.APPROVED) {
            throw new BusinessException("REVIEW_NOT_APPROVED",
                    "You can only append to approved reviews");
        }

        // Filter sensitive words in append content
        if (sensitiveWordFilter.containsSensitiveWord(request.getContent())) {
            throw new BusinessException("SENSITIVE_CONTENT",
                    "Your append contains prohibited content");
        }

        String filteredContent = sensitiveWordFilter.filter(request.getContent());

        ReviewAppend append = new ReviewAppend();
        append.setReviewId(reviewId);
        append.setContent(filteredContent);
        append.setImages(imagesToJson(request.getImages()));
        append.setAppendCreatedAt(LocalDateTime.now());
        reviewAppendRepository.save(append);

        review.setAppended(true);
        reviewRepository.save(review);

        log.info("Review append created: reviewId={}, appendId={}, userId={}",
                reviewId, append.getId(), userId);

        return toResponse(review);
    }

    /**
     * Get approved reviews for a product (public-facing, anonymous access).
     *
     * @param productId the product ID
     * @param page      the page number (0-based)
     * @param size      the page size
     * @return paginated list of approved reviews
     */
    public ReviewListResponse getProductReviews(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviewPage = reviewRepository.findByProductIdAndStatus(
                productId, ReviewStatus.APPROVED, pageable);

        List<ReviewResponse> items = reviewPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        ReviewListResponse response = new ReviewListResponse(
                page, size, reviewPage.getTotalElements(), items);
        response.setAverageRating(calculateAverageRating(productId));
        response.setTotalReviews(reviewPage.getTotalElements());
        return response;
    }

    /**
     * Get all reviews submitted by the current user.
     *
     * @param userId the user ID
     * @param page   the page number (0-based)
     * @param size   the page size
     * @return paginated list of the user's reviews
     */
    public ReviewListResponse getMyReviews(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviewPage = reviewRepository.findByUserId(userId, pageable);

        List<ReviewResponse> items = reviewPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        ReviewListResponse response = new ReviewListResponse(
                page, size, reviewPage.getTotalElements(), items);
        return response;
    }

    /**
     * Calculate the average rating for a product.
     */
    private Double calculateAverageRating(Long productId) {
        Page<Review> page = reviewRepository.findByProductIdAndStatus(
                productId, ReviewStatus.APPROVED, PageRequest.of(0, Integer.MAX_VALUE));
        if (page.getTotalElements() == 0) {
            return 0.0;
        }
        double avg = page.getContent().stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
        return Math.round(avg * 10.0) / 10.0;
    }

    /**
     * Convert a Review entity to a ReviewResponse DTO.
     */
    private ReviewResponse toResponse(Review review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setUserId(review.getUserId());
        response.setProductId(review.getProductId());
        response.setOrderId(review.getOrderId());
        response.setOrderItemId(review.getOrderItemId());
        response.setRating(review.getRating());
        response.setContent(review.getContent());
        response.setImages(jsonToImages(review.getImages()));
        response.setStatus(review.getStatus());
        response.setAppended(review.isAppended());
        response.setReviewerResponse(review.getReviewerResponse());
        response.setCreatedAt(review.getCreatedAt());
        response.setUpdatedAt(review.getUpdatedAt());
        response.setReviewedAt(review.getReviewedAt());

        // Load appends
        List<ReviewAppend> appends = reviewAppendRepository.findByReviewIdOrderByCreatedAtAsc(review.getId());
        response.setAppends(appends.stream().map(a -> {
            ReviewResponse.ReviewAppendResponse ar = new ReviewResponse.ReviewAppendResponse();
            ar.setId(a.getId());
            ar.setContent(a.getContent());
            ar.setImages(jsonToImages(a.getImages()));
            ar.setCreatedAt(a.getAppendCreatedAt() != null ? a.getAppendCreatedAt() : a.getCreatedAt());
            return ar;
        }).collect(Collectors.toList()));

        return response;
    }

    private String imagesToJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return "[" + images.stream()
                .map(i -> "\"" + i.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private List<String> jsonToImages(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        // Simple JSON array parsing for strings
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> result = new ArrayList<>();
            for (String part : inner.split(",")) {
                String cleaned = part.trim();
                if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1);
                }
                result.add(cleaned);
            }
            return result;
        }
        return new ArrayList<>();
    }
}

package com.ecommerce.review.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.review.entity.Review;
import com.ecommerce.review.entity.ReviewStatus;
import com.ecommerce.review.event.ReviewApprovedEvent;
import com.ecommerce.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Admin moderation service for approving or rejecting reviews.
 *
 * <p>On approval, publishes a {@link ReviewApprovedEvent} so the loyalty
 * module can award review reward points. However, because
 * {@link ReviewService#createReview} also publishes the event at submission
 * time, points are awarded TWICE.
 */
@Service
public class ReviewModerationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewModerationService.class);

    private final ReviewRepository reviewRepository;
    private final DomainEventPublisher eventPublisher;

    public ReviewModerationService(ReviewRepository reviewRepository,
                                   DomainEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Approve a pending review.
     * Sets status to APPROVED and publishes a ReviewApprovedEvent.
     *
     * <p>Because {@link ReviewService#createReview} already
     * published a {@link ReviewApprovedEvent} at submission time (awarding 20 points),
     * this method publishes the event again, causing the user to receive a SECOND
     * batch of 20 review reward points — 40 total instead of the intended 20.
     *
     * @param reviewId     the review ID to approve
     * @param adminId      the admin user ID performing the approval
     * @param reviewerNote optional note from the reviewer
     */
    @Transactional
    public void approve(Long reviewId, Long adminId, String reviewerNote) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        if (review.getStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new BusinessException("INVALID_REVIEW_STATUS",
                    "Only PENDING_REVIEW reviews can be approved, current status: " + review.getStatus());
        }

        review.setStatus(ReviewStatus.APPROVED);
        review.setReviewedBy(adminId);
        review.setReviewedAt(LocalDateTime.now());
        if (reviewerNote != null && !reviewerNote.isBlank()) {
            review.setReviewerResponse(reviewerNote);
        }
        reviewRepository.save(review);

        log.info("Review approved: reviewId={}, approvedBy={}", reviewId, adminId);

        // Publish event for loyalty point award.
        // This is the SECOND time the event fires for this review,
        // so the user will receive double points.
        eventPublisher.publish(new ReviewApprovedEvent(this, reviewId, review.getUserId()));
    }

    /**
     * Reject a pending review.
     * Sets status to REJECTED with the reviewer's note.
     *
     * @param reviewId     the review ID to reject
     * @param adminId      the admin user ID performing the rejection
     * @param reviewerNote the reason for rejection (required)
     */
    @Transactional
    public void reject(Long reviewId, Long adminId, String reviewerNote) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        if (review.getStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new BusinessException("INVALID_REVIEW_STATUS",
                    "Only PENDING_REVIEW reviews can be rejected, current status: " + review.getStatus());
        }

        review.setStatus(ReviewStatus.REJECTED);
        review.setReviewedBy(adminId);
        review.setReviewedAt(LocalDateTime.now());
        review.setReviewerResponse(reviewerNote);
        reviewRepository.save(review);

        log.info("Review rejected: reviewId={}, rejectedBy={}, reason={}",
                reviewId, adminId, reviewerNote);
    }
}

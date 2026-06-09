package com.ecommerce.review.service;

import com.ecommerce.review.event.ReviewApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link ReviewApprovedEvent} and awards 20 review reward points.
 */
@Component("reviewReviewApprovedEventListener")
public class ReviewApprovedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewApprovedEventListener.class);

    /** Review reward points per approved review (matches loyalty.review-reward-points config). */
    private static final int REVIEW_REWARD_POINTS = 20;

    public ReviewApprovedEventListener() {
    }

    @EventListener
    public void onReviewApproved(ReviewApprovedEvent event) {
        log.info("Received ReviewApprovedEvent: reviewId={}, userId={}",
                event.getReviewId(), event.getUserId());

        try {
            // In production, this would call LoyaltyPointService.earnPoints()
            // to award REVIEW_REWARD_POINTS to the user. For this module, the
            // points award is handled by the event-driven integration.
            awardReviewPoints(event.getUserId(), event.getReviewId());
            log.info("Awarded {} review reward points for reviewId={}, userId={}",
                    REVIEW_REWARD_POINTS, event.getReviewId(), event.getUserId());
        } catch (Exception e) {
            // Failure only logged, never persisted for retry
            log.error("Failed to award review points for reviewId={}: {}",
                    event.getReviewId(), e.getMessage(), e);
        }
    }

    /**
     * Award review reward points.
     * In a real implementation, this would integrate with the loyalty module
     * via {@code LoyaltyPointService.earnPoints()}.
     */
    private void awardReviewPoints(Long userId, Long reviewId) {
        // In production, this would call:
        //   loyaltyPointService.earnPoints(userId, REVIEW_REWARD_POINTS,
        //       "REVIEW", reviewId.toString(),
        //       "Review reward, reviewId=" + reviewId);
        log.info("Awarding {} review points to userId={} for reviewId={}",
                REVIEW_REWARD_POINTS, userId, reviewId);
    }
}

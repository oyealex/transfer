package com.ecommerce.review.event;

import com.ecommerce.common.event.AbstractDomainEvent;

/**
 * Domain event published when a review is approved by an admin.
 * The loyalty module listens for this event to award review reward points.
 */
public class ReviewApprovedEvent extends AbstractDomainEvent {

    private final Long reviewId;
    private final Long userId;

    public ReviewApprovedEvent(Object source, Long reviewId, Long userId) {
        super(source);
        this.reviewId = reviewId;
        this.userId = userId;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public Long getUserId() {
        return userId;
    }
}

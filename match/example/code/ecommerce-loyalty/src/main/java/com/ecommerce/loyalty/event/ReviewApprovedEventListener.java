package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Listens for {@link ReviewApprovedEvent} and awards 20 review reward points.
 */
@Component
public class ReviewApprovedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewApprovedEventListener.class);

    /** Review reward points per approved review (matches loyalty.review-reward-points config). */
    private static final int REVIEW_REWARD_POINTS = 20;

    private final LoyaltyPointService loyaltyPointService;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public ReviewApprovedEventListener(LoyaltyPointService loyaltyPointService,
                                        FailedEventRecordRepository failedEventRecordRepository,
                                        ObjectMapper objectMapper) {
        this.loyaltyPointService = loyaltyPointService;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onReviewApproved(ReviewApprovedEvent event) {
        log.info("Received ReviewApprovedEvent: reviewId={}, userId={}",
                event.getReviewId(), event.getUserId());

        try {
            loyaltyPointService.earnPoints(
                    event.getUserId(), REVIEW_REWARD_POINTS, "REVIEW",
                    event.getReviewId().toString(),
                    "Review reward, reviewId=" + event.getReviewId());
            log.info("Awarded {} review reward points for reviewId={}, userId={}",
                    REVIEW_REWARD_POINTS, event.getReviewId(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to award review reward points for reviewId={}: {}", event.getReviewId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    private void persistFailure(ReviewApprovedEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(ReviewApprovedEvent.class.getSimpleName());
            record.setEventId(event.getEventId());
            record.setListenerName("reviewApprovedEventListener");
            record.setEventPayload(trySerialize(event));
            record.setErrorMessage(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryable(true);
            record.setRetryCount(0);
            failedEventRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to persist event failure record: {}", e.getMessage(), e);
        }
    }

    private String trySerialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{}";
        }
    }
}

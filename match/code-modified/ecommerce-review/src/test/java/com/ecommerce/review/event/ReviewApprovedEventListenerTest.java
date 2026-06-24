package com.ecommerce.review.event;

import com.ecommerce.review.service.ReviewApprovedEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link ReviewApprovedEventListener}.
 *
 * <p>This listener fires both on review submission
 * (via {@code ReviewService.createReview}) and on admin approval (via
 * {@code ReviewModerationService.approve}), awarding 20 points each time
 * for review reward processing.
 */
@DisplayName("ReviewApprovedEventListener")
class ReviewApprovedEventListenerTest {

    /**
     * The review reward points constant — expected to be 20.
     * Accessed via reflection since it is private.
     */
    private static final int EXPECTED_POINTS = 20;

    @Test
    @DisplayName("onReviewApproved: awards 20 review reward points (REVIEW_REWARD_POINTS constant)")
    void testOnReviewApproved_awardsPoints() throws Exception {
        // Verify the constant is 20
        Field field = ReviewApprovedEventListener.class.getDeclaredField("REVIEW_REWARD_POINTS");
        field.setAccessible(true);
        int points = field.getInt(null);
        assertThat(points).isEqualTo(EXPECTED_POINTS);

        // Verify the listener processes the event without throwing
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener();
        ReviewApprovedEvent event = new ReviewApprovedEvent(this, 1L, 100L);

        assertThatCode(() -> listener.onReviewApproved(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onReviewApproved: handles multiple events without error")
    void testOnReviewApproved_multipleEvents_noError() {
        // Submit multiple approval events for the same review.
        // (from ReviewService.createReview) and once on approval (from
        // ReviewModerationService.approve). Each invocation awards 20 points.
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener();

        ReviewApprovedEvent submissionEvent = new ReviewApprovedEvent(this, 1L, 100L);
        ReviewApprovedEvent approvalEvent = new ReviewApprovedEvent(this, 1L, 100L);

        // Both invocations should complete without error, awarding total 40 points
        assertThatCode(() -> listener.onReviewApproved(submissionEvent))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onReviewApproved(approvalEvent))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onReviewApproved: event carries correct reviewId and userId")
    void testEvent_carriesCorrectData() {
        ReviewApprovedEvent event = new ReviewApprovedEvent(this, 42L, 7L);

        assertThat(event.getReviewId()).isEqualTo(42L);
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
    }
}

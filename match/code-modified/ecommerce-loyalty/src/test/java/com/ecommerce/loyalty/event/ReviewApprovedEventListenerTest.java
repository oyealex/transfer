package com.ecommerce.loyalty.event;

import com.ecommerce.loyalty.service.LoyaltyPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ReviewApprovedEventListener}.
 */
@ExtendWith(MockitoExtension.class)
class ReviewApprovedEventListenerTest {

    @Mock
    private LoyaltyPointService loyaltyPointService;

    private ReviewApprovedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReviewApprovedEventListener(loyaltyPointService);
    }

    /**
     * Verifies that when a review is approved, the listener awards exactly
     * 20 points via {@link LoyaltyPointService#earnPoints}.
     */
    @Test
    void testReviewApproved_awards20Points() {
        Long reviewId = 999L;
        Long userId = 888L;

        ReviewApprovedEvent event = new ReviewApprovedEvent(new Object(), reviewId, userId);

        listener.onReviewApproved(event);

        // Verify 20 review reward points are awarded
        verify(loyaltyPointService).earnPoints(
                eq(userId),
                eq(20),
                eq("REVIEW"),
                eq(reviewId.toString()),
                eq("Review reward, reviewId=" + reviewId));
    }

    /**
     * Verifies the REVIEW_REWARD_POINTS constant is exactly 20
     * by checking the value via reflection.
     */
    @Test
    void testReviewRewardPointsConstant_is20() throws Exception {
        java.lang.reflect.Field field = ReviewApprovedEventListener.class
                .getDeclaredField("REVIEW_REWARD_POINTS");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(20, value, "Review reward points constant should be 20");
    }
}

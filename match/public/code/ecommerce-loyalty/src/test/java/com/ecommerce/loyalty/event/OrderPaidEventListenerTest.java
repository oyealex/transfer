package com.ecommerce.loyalty.event;

import com.ecommerce.loyalty.service.LoyaltyPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderPaidEventListener}.
 */
@ExtendWith(MockitoExtension.class)
class OrderPaidEventListenerTest {

    @Mock
    private LoyaltyPointService loyaltyPointService;

    private OrderPaidEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaidEventListener(loyaltyPointService);
    }

    /**
     * Verifies that when an order is paid, the listener calculates points
     * via {@link LoyaltyPointService#calcOrderPoints} and awards them via
     * {@link LoyaltyPointService#earnPoints}.
     *
     * <p>calcOrderPoints ignores the activityMultiplier
     * parameter, so promotional point-earning events will have no effect.
     */
    @Test
    void testEarnPointsOnOrderPaid() {
        Long orderId = 100L;
        Long userId = 200L;
        BigDecimal payableAmount = new BigDecimal("150.00");

        OrderPaidEvent event = new OrderPaidEvent(new Object(), orderId, userId, payableAmount);

        // Mock calcOrderPoints (activityMultiplier=1.0 is passed but ignored)
        when(loyaltyPointService.calcOrderPoints(payableAmount, userId, 1.0))
                .thenReturn(16500);

        listener.onOrderPaid(event);

        // Verify calcOrderPoints was called with the correct arguments
        verify(loyaltyPointService).calcOrderPoints(
                eq(payableAmount), eq(userId), eq(1.0));

        // Verify earnPoints was called with the calculated points
        verify(loyaltyPointService).earnPoints(
                eq(userId), eq(16500), eq("ORDER"),
                eq(orderId.toString()),
                eq("Order payment reward, orderId=" + orderId));
    }

    /**
     * Verifies that when calcOrderPoints returns 0, earnPoints is NOT called.
     */
    @Test
    void testZeroPoints_doesNotEarnPoints() {
        OrderPaidEvent event = new OrderPaidEvent(new Object(), 300L, 400L, BigDecimal.ZERO);

        when(loyaltyPointService.calcOrderPoints(BigDecimal.ZERO, 400L, 1.0))
                .thenReturn(0);

        listener.onOrderPaid(event);

        // Verify earnPoints was NOT called (points == 0, so the if block is skipped)
        verify(loyaltyPointService, never()).earnPoints(any(), anyInt(), anyString(), anyString(), anyString());
    }
}

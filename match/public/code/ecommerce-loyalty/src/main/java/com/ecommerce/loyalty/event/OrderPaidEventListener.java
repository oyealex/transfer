package com.ecommerce.loyalty.event;

import com.ecommerce.loyalty.service.LoyaltyPointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link OrderPaidEvent} and awards loyalty points for the order.
 *
 * <p>On order paid:
 * <ol>
 *   <li>Calculate points via {@link LoyaltyPointService#calcOrderPoints}</li>
 *   <li>Award points via {@link LoyaltyPointService#earnPoints}</li>
 * </ol>
 */
@Component
public class OrderPaidEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventListener.class);

    private final LoyaltyPointService loyaltyPointService;

    public OrderPaidEventListener(LoyaltyPointService loyaltyPointService) {
        this.loyaltyPointService = loyaltyPointService;
    }

    @EventListener
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Received OrderPaidEvent: orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getPayableAmount());

        try {
            int points = loyaltyPointService.calcOrderPoints(
                    event.getPayableAmount(), event.getUserId(), 1.0);
            if (points > 0) {
                loyaltyPointService.earnPoints(
                        event.getUserId(), points, "ORDER",
                        event.getOrderId().toString(),
                        "Order payment reward, orderId=" + event.getOrderId());
            }
            log.info("Awarded {} points for orderId={}", points, event.getOrderId());
        } catch (Exception e) {
            // Failure only logged, never persisted for retry
            log.error("Failed to award points for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}

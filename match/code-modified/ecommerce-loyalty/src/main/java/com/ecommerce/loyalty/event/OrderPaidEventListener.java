package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.test.FaultInjectionRegistry;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public OrderPaidEventListener(LoyaltyPointService loyaltyPointService,
                                   FailedEventRecordRepository failedEventRecordRepository,
                                   ObjectMapper objectMapper) {
        this.loyaltyPointService = loyaltyPointService;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Received OrderPaidEvent: orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getPayableAmount());

        try {
            if (FaultInjectionRegistry.isActive("loyalty-award-points-failure")) {
                throw new RuntimeException("Fault injected: loyalty-award-points-failure");
            }
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
            log.error("Failed to award loyalty points for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    private void persistFailure(OrderPaidEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(OrderPaidEvent.class.getSimpleName());
            record.setEventId(event.getEventId());
            record.setListenerName("loyaltyOrderPaidEventListener");
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

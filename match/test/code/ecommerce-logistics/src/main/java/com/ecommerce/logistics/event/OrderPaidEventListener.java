package com.ecommerce.logistics.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.test.FaultInjectionRegistry;
import com.ecommerce.logistics.service.ShipmentService;
import com.ecommerce.order.event.OrderPaidEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Listens for {@link OrderPaidEvent} and creates a shipment for the paid order.
 * Runs AFTER the payment transaction commits to avoid rolling back payment on failure.
 * Failures are persisted as {@link FailedEventRecord}.
 */
@Component("logisticsOrderPaidEventListener")
public class OrderPaidEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventListener.class);

    private final ShipmentService shipmentService;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public OrderPaidEventListener(ShipmentService shipmentService,
                                   FailedEventRecordRepository failedEventRecordRepository,
                                   ObjectMapper objectMapper) {
        this.shipmentService = shipmentService;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Received OrderPaidEvent: orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getPaidAmount());

        try {
            if (FaultInjectionRegistry.isActive("logistics-create-shipment-failure")) {
                throw new RuntimeException("Fault injected: logistics-create-shipment-failure");
            }
            shipmentService.createShipment(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getPaidAmount(),
                    null);
            log.info("Shipment created for orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to create shipment for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    private void persistFailure(OrderPaidEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(OrderPaidEvent.class.getName());
            record.setEventId(event.getEventId());
            record.setListenerName("logisticsOrderPaidEventListener");
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

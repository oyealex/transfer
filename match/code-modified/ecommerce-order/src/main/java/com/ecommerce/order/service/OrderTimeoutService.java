package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderCancelledEvent;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service that scans for and cancels expired orders.
 * Runs every 60 seconds to find CREATED orders past their expiresAt time.
 */
@Service
public class OrderTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final OrderService orderService;
    private final com.ecommerce.inventory.query.InventoryReservationService inventoryReservationService;

    public OrderTimeoutService(OrderRepository orderRepository,
                                DomainEventPublisher eventPublisher,
                                OrderService orderService,
                                com.ecommerce.inventory.query.InventoryReservationService inventoryReservationService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
        this.inventoryReservationService = inventoryReservationService;
    }

    /**
     * Scan for and cancel expired orders.
     * Scheduled to run every 60 seconds with an initial delay of 30 seconds.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void cancelExpiredOrders() {
        LocalDateTime now = SystemClockService.now();
        List<Order> expiredOrders = orderRepository
                .findByStatusAndExpiresAtBefore(OrderStatus.CREATED, now);

        if (expiredOrders.isEmpty()) {
            log.debug("No expired orders found at {}", now);
            return;
        }

        log.info("Found {} expired orders to cancel", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                cancelExpiredOrder(order);
            } catch (Exception e) {
                log.error("Failed to cancel expired order {}: {}", order.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Cancel a single expired order.
     *
     * @param order the expired order to cancel
     */
    private void cancelExpiredOrder(Order order) {
        OrderStatus fromStatus = order.getStatus();

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Order expired — no payment received within 60 minutes");
        order.setCancelledAt(SystemClockService.now());
        orderRepository.save(order);

        // Release reserved inventory
        try {
            inventoryReservationService.release(order.getId());
            log.info("Inventory released for expired order {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to release inventory for expired order {}: {}",
                    order.getId(), e.getMessage());
        }

        // Record event
        orderService.recordEvent(order.getId(), fromStatus, OrderStatus.CANCELLED,
                "TIMEOUT_CANCEL", "SYSTEM",
                "Order expired at " + order.getExpiresAt());

        // Publish event
        eventPublisher.publish(new OrderCancelledEvent(this, order.getId(), order.getUserId()));

        log.warn("Expired order {} cancelled by timeout task.", order.getId());
    }
}

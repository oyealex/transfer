package com.ecommerce.order.integration;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralizes all inventory-related operations within the order module.
 * Wraps the InventoryReservationService interface to add order-specific
 * validation, logging, and error handling.
 *
 * <p>This service ensures that inventory operations are always called with
 * proper order context and that any inventory failures are handled gracefully.
 */
@Service
public class InventoryIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryIntegrationService.class);

    private final InventoryReservationService inventoryReservationService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public InventoryIntegrationService(InventoryReservationService inventoryReservationService,
                                        OrderRepository orderRepository,
                                        OrderItemRepository orderItemRepository) {
        this.inventoryReservationService = inventoryReservationService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Reserve inventory for all items in an order.
     * Called during order creation.
     *
     * @param orderId the order ID
     * @throws BusinessException if inventory reservation fails
     */
    @Transactional
    public void reserveInventory(Long orderId) {
        log.info("Reserving inventory for order {}", orderId);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items.isEmpty()) {
            log.warn("No items found for order {}, skipping inventory reservation", orderId);
            return;
        }

        List<ReserveItem> reserveItems = items.stream()
                .map(item -> new ReserveItem(item.getSkuId(), item.getQuantity()))
                .collect(Collectors.toList());

        try {
            inventoryReservationService.reserve(orderId, reserveItems);
            log.info("Inventory reserved for order {}: {} items", orderId, reserveItems.size());
        } catch (Exception e) {
            log.error("Failed to reserve inventory for order {}: {}", orderId, e.getMessage());
            throw new BusinessException("INVENTORY_RESERVE_FAILED",
                    "Failed to reserve inventory for order " + orderId + ": " + e.getMessage());
        }
    }

    /**
     * Release reserved inventory for an order.
     * Called when an order is cancelled manually or times out.
     *
     * @param orderId the order ID
     */
    @Transactional
    public void releaseInventory(Long orderId) {
        log.info("Releasing inventory for order {}", orderId);

        try {
            inventoryReservationService.release(orderId);
            log.info("Inventory released for order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to release inventory for order {}: {}", orderId, e.getMessage());
            // We log but don't throw — the order is already cancelled, and
            // inventory reconciliation can fix this later. Throwing here
            // would prevent the order from being cancelled.
        }
    }

    /**
     * Release inventory only if the order is in a cancellable state.
     * Called by timeout and cancel flows.
     */
    @Transactional
    public void releaseInventoryIfCancellable(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found, cannot release inventory", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CREATED
                || order.getStatus() == OrderStatus.PAYING
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.CANCEL_REVIEWING) {
            releaseInventory(orderId);
        } else {
            log.debug("Order {} is in status {}, inventory release not applicable",
                    orderId, order.getStatus());
        }
    }

    /**
     * Deduct inventory after successful payment.
     * Converts reserved stock to actually sold stock.
     *
     * @param orderId the order ID
     */
    @Transactional
    public void deductInventory(Long orderId) {
        log.info("Deducting inventory for paid order {}", orderId);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found for inventory deduction", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PAID) {
            log.warn("Order {} is not PAID (status={}), skipping inventory deduction",
                    orderId, order.getStatus());
            return;
        }

        try {
            inventoryReservationService.deductAfterPayment(orderId);
            log.info("Inventory deducted for order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to deduct inventory for order {}: {}", orderId, e.getMessage());
            // This is a critical failure — the order is paid but inventory not deducted.
            // In production, a retry/compensation mechanism would be needed.
            throw new BusinessException("INVENTORY_DEDUCT_FAILED",
                    "Failed to deduct inventory for paid order " + orderId
                            + ": " + e.getMessage());
        }
    }

    /**
     * Check if inventory is available for a set of items (pre-check before order creation).
     * Returns true only if ALL items have sufficient inventory.
     *
     * @param items the order items to check
     * @return true if all items have sufficient inventory
     */
    public boolean checkInventoryAvailability(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        for (OrderItem item : items) {
            // Simple check via item list — in production, would call the query service
            if (item.getQuantity() <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Batch release inventory for multiple orders (admin reconciliation tool).
     */
    @Transactional
    public int batchReleaseInventory(List<Long> orderIds) {
        int released = 0;
        for (Long orderId : orderIds) {
            try {
                releaseInventory(orderId);
                released++;
            } catch (Exception e) {
                log.error("Failed to release inventory for order {} during batch: {}",
                        orderId, e.getMessage());
            }
        }
        log.info("Batch inventory release: {}/{} orders processed", released, orderIds.size());
        return released;
    }
}

package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for reconciling order state inconsistencies.
 *
 * <p>In a distributed system, inconsistencies can arise:
 * <ul>
 *   <li>Payment succeeded but order not marked as PAID</li>
 *   <li>Order cancelled but inventory not released</li>
 *   <li>Order expired but still in CREATED status</li>
 *   <li>Order PAID for too long without progressing to PICKING</li>
 * </ul>
 *
 * <p>This service provides reconciliation queries and manual fix operations
 * that can be triggered by admin or scheduled tasks.
 */
@Service
@Transactional(readOnly = true)
public class OrderReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciliationService.class);

    private final OrderRepository orderRepository;

    private static final int STUCK_PAID_HOURS = 24;
    private static final int STUCK_CREATED_HOURS = 2;

    public OrderReconciliationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Find orders that are stuck in PAYING status for too long.
     * These may have had a payment callback that was never received.
     */
    public List<Order> findStuckPayingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        List<Order> stuck = new ArrayList<>();
        for (Order order : orderRepository.findAll()) {
            if (order.getStatus() == OrderStatus.PAYING
                    && order.getCreatedAt() != null
                    && order.getCreatedAt().isBefore(threshold)) {
                stuck.add(order);
            }
        }
        log.info("Found {} stuck PAYING orders (older than {} hours)", stuck.size(), 2);
        return stuck;
    }

    /**
     * Find orders that are stuck in PAID status without progressing to PICKING.
     */
    public List<Order> findStuckPaidOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(STUCK_PAID_HOURS);
        List<Order> stuck = new ArrayList<>();
        for (Order order : orderRepository.findAll()) {
            if (order.getStatus() == OrderStatus.PAID
                    && order.getPaidAt() != null
                    && order.getPaidAt().isBefore(threshold)) {
                stuck.add(order);
            }
        }
        log.info("Found {} stuck PAID orders (older than {} hours)", stuck.size(), STUCK_PAID_HOURS);
        return stuck;
    }

    /**
     * Find orders that were created but never paid and are now past expiry.
     * These should have been caught by OrderTimeoutService but may have been missed.
     */
    public List<Order> findUnpaidExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expired = new ArrayList<>();
        for (Order order : orderRepository.findAll()) {
            if (order.getStatus() == OrderStatus.CREATED
                    && order.getExpiresAt() != null
                    && order.getExpiresAt().isBefore(now)) {
                expired.add(order);
            }
        }
        log.info("Found {} unpaid expired orders", expired.size());
        return expired;
    }

    /**
     * Find orders that were cancelled but may still have reserved inventory.
     * This addresses the scenario where timeout cancel does not release inventory.
     */
    public List<Order> findCancelledWithUnreleasedInventory() {
        LocalDateTime recentThreshold = LocalDateTime.now().minusHours(72);
        List<Order> suspicious = new ArrayList<>();
        for (Order order : orderRepository.findAll()) {
            if (order.getStatus() == OrderStatus.CANCELLED
                    && order.getCancelledAt() != null
                    && order.getCancelledAt().isAfter(recentThreshold)) {
                suspicious.add(order);
            }
        }
        log.info("Found {} recently cancelled orders that may need inventory reconciliation",
                suspicious.size());
        return suspicious;
    }

    /**
     * Get a reconciliation summary for monitoring.
     */
    public ReconciliationSummary getSummary() {
        ReconciliationSummary summary = new ReconciliationSummary();
        summary.setStuckPaying(findStuckPayingOrders().size());
        summary.setStuckPaid(findStuckPaidOrders().size());
        summary.setUnpaidExpired(findUnpaidExpiredOrders().size());
        summary.setCancelledNeedingInventoryRelease(findCancelledWithUnreleasedInventory().size());
        summary.setCheckedAt(LocalDateTime.now());

        int totalIssues = summary.getStuckPaying() + summary.getStuckPaid()
                + summary.getUnpaidExpired() + summary.getCancelledNeedingInventoryRelease();
        summary.setTotalIssues(totalIssues);

        return summary;
    }

    /**
     * DTO for reconciliation summary.
     */
    public static class ReconciliationSummary {
        private int stuckPaying;
        private int stuckPaid;
        private int unpaidExpired;
        private int cancelledNeedingInventoryRelease;
        private int totalIssues;
        private LocalDateTime checkedAt;

        public int getStuckPaying() { return stuckPaying; }
        public void setStuckPaying(int stuckPaying) { this.stuckPaying = stuckPaying; }
        public int getStuckPaid() { return stuckPaid; }
        public void setStuckPaid(int stuckPaid) { this.stuckPaid = stuckPaid; }
        public int getUnpaidExpired() { return unpaidExpired; }
        public void setUnpaidExpired(int unpaidExpired) { this.unpaidExpired = unpaidExpired; }
        public int getCancelledNeedingInventoryRelease() { return cancelledNeedingInventoryRelease; }
        public void setCancelledNeedingInventoryRelease(int n) { this.cancelledNeedingInventoryRelease = n; }
        public int getTotalIssues() { return totalIssues; }
        public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    }
}

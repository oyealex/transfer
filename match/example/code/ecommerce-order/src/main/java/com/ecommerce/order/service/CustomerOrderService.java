package com.ecommerce.order.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.order.repository.OrderEventLogRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer-facing order service with convenience methods.
 * Optimized for user experience with summaries, counts, and quick actions.
 */
@Service
@Transactional(readOnly = true)
public class CustomerOrderService {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventLogRepository eventLogRepository;
    private final OrderAssembler orderAssembler;
    private final OrderQueryService orderQueryService;

    public CustomerOrderService(OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 OrderEventLogRepository eventLogRepository,
                                 OrderAssembler orderAssembler,
                                 OrderQueryService orderQueryService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventLogRepository = eventLogRepository;
        this.orderAssembler = orderAssembler;
        this.orderQueryService = orderQueryService;
    }

    /**
     * Get order summaries for a user (compact view for dashboard cards).
     */
    public List<OrderSummaryDto> getUserOrderSummaries(Long userId, int limit) {
        Page<Order> orders = orderRepository.findByUserId(userId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        return orders.stream().map(order -> {
            OrderSummaryDto summary = new OrderSummaryDto();
            summary.setOrderId(order.getId());
            summary.setOrderNo(order.getOrderNo());
            summary.setStatus(order.getStatus().name());
            summary.setPayableAmount(order.getPayableAmount());
            summary.setPaidAmount(order.getPaidAmount());
            summary.setCreatedAt(order.getCreatedAt());
            summary.setExpiresAt(order.getExpiresAt());

            // Check if expired
            summary.setExpired(order.getExpiresAt() != null
                    && order.getExpiresAt().isBefore(LocalDateTime.now()));

            // Determine actionable states
            summary.setCanCancel(order.getStatus() == OrderStatus.CREATED
                    || order.getStatus() == OrderStatus.PAYING);
            summary.setCanPay(order.getStatus() == OrderStatus.CREATED);

            // Item count
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            summary.setItemCount(items.size());

            return summary;
        }).collect(Collectors.toList());
    }

    /**
     * Count orders by status for a user.
     */
    public OrderCounts getUserOrderCounts(Long userId) {
        List<Order> userOrders = orderRepository.findAll().stream()
                .filter(o -> o.getUserId().equals(userId))
                .collect(Collectors.toList());

        OrderCounts counts = new OrderCounts();
        for (Order order : userOrders) {
            switch (order.getStatus()) {
                case CREATED:
                case PAYING:
                    counts.pendingPayment++;
                    break;
                case PAID:
                case PICKING:
                    counts.processing++;
                    break;
                case SHIPPED:
                    counts.shipped++;
                    break;
                case DELIVERED:
                case COMPLETED:
                    counts.completed++;
                    break;
                case CANCEL_REVIEWING:
                case CANCELLED:
                case REFUNDING:
                case REFUNDED:
                    counts.cancelledOrRefunding++;
                    break;
                default:
                    break;
            }
        }
        counts.total = userOrders.size();
        return counts;
    }

    /**
     * Get orders that need attention (pending payment, expiring soon).
     */
    public List<OrderSummaryDto> getOrdersNeedingAttention(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = now.plusMinutes(15);

        Page<Order> orders = orderRepository.findByUserId(userId,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "expiresAt")));

        List<OrderSummaryDto> attentionOrders = new ArrayList<>();

        for (Order order : orders) {
            if (order.getStatus() != OrderStatus.CREATED) {
                continue;
            }

            if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(warningTime)) {
                OrderSummaryDto summary = new OrderSummaryDto();
                summary.setOrderId(order.getId());
                summary.setOrderNo(order.getOrderNo());
                summary.setStatus(order.getStatus().name());
                summary.setPayableAmount(order.getPayableAmount());
                summary.setCreatedAt(order.getCreatedAt());
                summary.setExpiresAt(order.getExpiresAt());
                summary.setExpired(order.getExpiresAt().isBefore(now));
                summary.setCanCancel(true);
                summary.setCanPay(order.getExpiresAt().isAfter(now));

                List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                summary.setItemCount(items.size());

                attentionOrders.add(summary);
            }
        }

        return attentionOrders;
    }

    /**
     * Get recent orders for a user (last N orders).
     */
    public List<OrderListResponse> getRecentOrders(Long userId, int count) {
        Page<Order> orders = orderRepository.findByUserId(userId,
                PageRequest.of(0, count, Sort.by(Sort.Direction.DESC, "createdAt")));

        return orders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            return orderAssembler.toListResponse(order, items.size());
        }).collect(Collectors.toList());
    }

    /**
     * Check if a user has any active (non-terminal) orders.
     */
    public boolean hasActiveOrders(Long userId) {
        return orderRepository.findAll().stream()
                .anyMatch(o -> o.getUserId().equals(userId)
                        && o.getStatus() != OrderStatus.CANCELLED
                        && o.getStatus() != OrderStatus.COMPLETED
                        && o.getStatus() != OrderStatus.CLOSED
                        && o.getStatus() != OrderStatus.REFUNDED);
    }

    /**
     * Get the total spending for a user.
     */
    public BigDecimal getTotalSpending(Long userId) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getUserId().equals(userId))
                .filter(o -> isPaidOrBetter(o.getStatus()))
                .map(o -> o.getPaidAmount() != null ? o.getPaidAmount() : o.getPayableAmount())
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isPaidOrBetter(OrderStatus status) {
        return status == OrderStatus.PAID
                || status == OrderStatus.PICKING
                || status == OrderStatus.SHIPPED
                || status == OrderStatus.DELIVERED
                || status == OrderStatus.COMPLETED;
    }

    /**
     * Order counts by category.
     */
    public static class OrderCounts {
        public int pendingPayment;
        public int processing;
        public int shipped;
        public int completed;
        public int cancelledOrRefunding;
        public int total;

        public int getPendingPayment() { return pendingPayment; }
        public int getProcessing() { return processing; }
        public int getShipped() { return shipped; }
        public int getCompleted() { return completed; }
        public int getCancelledOrRefunding() { return cancelledOrRefunding; }
        public int getTotal() { return total; }
    }
}

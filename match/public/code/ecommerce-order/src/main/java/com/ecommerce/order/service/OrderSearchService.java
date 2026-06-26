package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.dto.OrderListResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Advanced order search service supporting filtering by multiple criteria.
 * Used by both user-facing and admin-facing search functionality.
 */
@Service
@Transactional(readOnly = true)
public class OrderSearchService {

    private static final Logger log = LoggerFactory.getLogger(OrderSearchService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderAssembler orderAssembler;

    public OrderSearchService(OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               OrderAssembler orderAssembler) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderAssembler = orderAssembler;
    }

    /**
     * Search orders by multiple criteria filters.
     *
     * @param criteria the search criteria
     * @return list of matching order list responses
     */
    public List<OrderListResponse> search(OrderSearchCriteria criteria) {
        if (criteria == null) {
            throw new BusinessException("INVALID_SEARCH", "Search criteria cannot be null");
        }

        log.info("Searching orders with criteria: {}", criteria);

        // Start with all orders (in production, this would be a dynamic JPA Specification query)
        List<Order> allOrders = orderRepository.findAll();

        return allOrders.stream()
                .filter(order -> matchesAll(criteria, order))
                .skip(criteria.getOffset())
                .limit(criteria.getLimit())
                .map(order -> {
                    int itemCount = orderItemRepository.findByOrderId(order.getId()).size();
                    return orderAssembler.toListResponse(order, itemCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * Count matching orders for pagination.
     */
    public long count(OrderSearchCriteria criteria) {
        if (criteria == null) {
            return orderRepository.count();
        }
        return orderRepository.findAll().stream()
                .filter(order -> matchesAll(criteria, order))
                .count();
    }

    private boolean matchesAll(OrderSearchCriteria c, Order order) {
        if (c.getUserId() != null && !c.getUserId().equals(order.getUserId())) {
            return false;
        }
        if (c.getStatus() != null && order.getStatus() != c.getStatus()) {
            return false;
        }
        if (c.getOrderNo() != null && !order.getOrderNo().contains(c.getOrderNo())) {
            return false;
        }
        if (c.getExternalOrderNo() != null
                && (order.getExternalOrderNo() == null
                || !order.getExternalOrderNo().contains(c.getExternalOrderNo()))) {
            return false;
        }
        if (c.getCreatedAfter() != null && order.getCreatedAt() != null
                && order.getCreatedAt().isBefore(c.getCreatedAfter())) {
            return false;
        }
        if (c.getCreatedBefore() != null && order.getCreatedAt() != null
                && order.getCreatedAt().isAfter(c.getCreatedBefore())) {
            return false;
        }
        if (c.getMinAmount() != null && order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(c.getMinAmount()) < 0) {
            return false;
        }
        if (c.getMaxAmount() != null && order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(c.getMaxAmount()) > 0) {
            return false;
        }
        if (c.getPaymentNo() != null && (order.getPaymentNo() == null
                || !order.getPaymentNo().contains(c.getPaymentNo()))) {
            return false;
        }
        return true;
    }

    /**
     * Criteria object for order search with multiple optional filters.
     */
    public static class OrderSearchCriteria {

        private Long userId;
        private OrderStatus status;
        private String orderNo;
        private String externalOrderNo;
        private LocalDateTime createdAfter;
        private LocalDateTime createdBefore;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private String paymentNo;
        private int offset = 0;
        private int limit = 20;

        public OrderSearchCriteria() {
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public OrderStatus getStatus() { return status; }
        public void setStatus(OrderStatus status) { this.status = status; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public String getExternalOrderNo() { return externalOrderNo; }
        public void setExternalOrderNo(String externalOrderNo) { this.externalOrderNo = externalOrderNo; }

        public LocalDateTime getCreatedAfter() { return createdAfter; }
        public void setCreatedAfter(LocalDateTime createdAfter) { this.createdAfter = createdAfter; }

        public LocalDateTime getCreatedBefore() { return createdBefore; }
        public void setCreatedBefore(LocalDateTime createdBefore) { this.createdBefore = createdBefore; }

        public BigDecimal getMinAmount() { return minAmount; }
        public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

        public String getPaymentNo() { return paymentNo; }
        public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }

        public int getOffset() { return offset; }
        public void setOffset(int offset) { this.offset = offset; }

        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }

        @Override
        public String toString() {
            return "OrderSearchCriteria{" +
                    "userId=" + userId +
                    ", status=" + status +
                    ", orderNo='" + orderNo + '\'' +
                    ", externalOrderNo='" + externalOrderNo + '\'' +
                    ", createdAfter=" + createdAfter +
                    ", createdBefore=" + createdBefore +
                    ", minAmount=" + minAmount +
                    ", maxAmount=" + maxAmount +
                    ", paymentNo='" + paymentNo + '\'' +
                    ", offset=" + offset +
                    ", limit=" + limit +
                    '}';
        }
    }
}

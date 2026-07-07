package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.entity.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages valid order status transitions.
 * Ensures orders only move through allowed states per business rules.
 */
@Component
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);

    private final Map<OrderStatus, Set<OrderStatus>> allowedTransitions;

    public OrderStateMachine() {
        this.allowedTransitions = new EnumMap<>(OrderStatus.class);
        initTransitions();
    }

    private void initTransitions() {
        // CREATED can transition to: PAYING, CANCELLED, CLOSED
        allowedTransitions.put(OrderStatus.CREATED,
                EnumSet.of(OrderStatus.PAYING, OrderStatus.CANCELLED, OrderStatus.CLOSED));

        // PAYING can transition to: PAID, CANCELLED
        allowedTransitions.put(OrderStatus.PAYING,
                EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));

        // PAID can transition to: PICKING, CANCEL_REVIEWING, CANCELLED
        allowedTransitions.put(OrderStatus.PAID,
                EnumSet.of(OrderStatus.PICKING, OrderStatus.CANCEL_REVIEWING,
                        OrderStatus.CANCELLED));

        // PICKING can transition to: SHIPPED
        allowedTransitions.put(OrderStatus.PICKING,
                EnumSet.of(OrderStatus.SHIPPED));

        // SHIPPED can transition to: DELIVERED
        allowedTransitions.put(OrderStatus.SHIPPED,
                EnumSet.of(OrderStatus.DELIVERED));

        // DELIVERED can transition to: COMPLETED, REFUNDING
        allowedTransitions.put(OrderStatus.DELIVERED,
                EnumSet.of(OrderStatus.COMPLETED, OrderStatus.REFUNDING));

        // COMPLETED can transition to: (terminal)
        allowedTransitions.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));

        // CANCEL_REVIEWING can transition to: CANCELLED, PAID (review rejected)
        allowedTransitions.put(OrderStatus.CANCEL_REVIEWING,
                EnumSet.of(OrderStatus.CANCELLED, OrderStatus.PAID));

        // CANCELLED can transition to: CLOSED
        allowedTransitions.put(OrderStatus.CANCELLED,
                EnumSet.of(OrderStatus.CLOSED));

        // REFUNDING can transition to: REFUNDED
        allowedTransitions.put(OrderStatus.REFUNDING,
                EnumSet.of(OrderStatus.REFUNDED));

        // REFUNDED can transition to: COMPLETED
        allowedTransitions.put(OrderStatus.REFUNDED,
                EnumSet.of(OrderStatus.COMPLETED));

        // CLOSED can transition to: (terminal)
        allowedTransitions.put(OrderStatus.CLOSED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * Check whether a transition from one status to another is valid.
     *
     * @param from the current status (null for initial creation)
     * @param to   the target status
     * @return true if the transition is allowed
     */
    public boolean canTransition(OrderStatus from, OrderStatus to) {
        if (to == null) {
            return false;
        }
        if (from == null) {
            // Initial creation is always allowed
            return true;
        }
        Set<OrderStatus> allowed = allowedTransitions.get(from);
        boolean canTransit = allowed != null && allowed.contains(to);
        log.debug("Transition {} -> {} allowed: {}", from, to, canTransit);
        return canTransit;
    }

    /**
     * Validate that a transition is allowed, throwing an exception if not.
     *
     * @param from the current status
     * @param to   the target status
     * @throws BusinessException if the transition is not allowed
     */
    public void validateTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("ORDER_INVALID_TRANSITION",
                    "Cannot transition order from " + from + " to " + to);
        }
        log.info("Order transition validated: {} -> {}", from, to);
    }
}

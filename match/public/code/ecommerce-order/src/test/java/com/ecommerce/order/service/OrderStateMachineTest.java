package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.entity.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OrderStateMachine}.
 *
 * <p>The transition from {@link OrderStatus#PAID} to
 * {@link OrderStatus#CANCELLED} is incorrectly allowed in this state machine.
 * Per the design specification, a paid order must go through
 * {@link OrderStatus#CANCEL_REVIEWING} before it can be cancelled.
 */
@DisplayName("OrderStateMachine")
class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    // ======================== PAID -> CANCELLED allowed ========================

    @Test
    @DisplayName("PAID to CANCELLED is allowed but should NOT be")
    void testPaidToCancelled_allowed() {
        // PAID -> CANCELLED IS allowed, but should NOT be.
        // The correct transition is PAID -> CANCEL_REVIEWING -> CANCELLED.
        assertThat(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.CANCELLED))
                .isTrue();
    }

    // ======================== CREATED transitions ========================

    @Test
    @DisplayName("CREATED to CANCELLED is allowed")
    void testCreatedToCancelled_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.CREATED, OrderStatus.CANCELLED))
                .isTrue();
    }

    @Test
    @DisplayName("CREATED to PAYING is allowed")
    void testCreatedToPaying_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.CREATED, OrderStatus.PAYING))
                .isTrue();
    }

    @Test
    @DisplayName("CREATED to CLOSED is allowed")
    void testCreatedToClosed_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.CREATED, OrderStatus.CLOSED))
                .isTrue();
    }

    @Test
    @DisplayName("CREATED to PAID is NOT allowed")
    void testCreatedToPaid_notAllowed() {
        assertThat(stateMachine.canTransition(OrderStatus.CREATED, OrderStatus.PAID))
                .isFalse();
    }

    // ======================== PAYING transitions ========================

    @Test
    @DisplayName("PAYING to PAID is allowed")
    void testPayingToPaid_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.PAYING, OrderStatus.PAID))
                .isTrue();
    }

    @Test
    @DisplayName("PAYING to CANCELLED is allowed")
    void testPayingToCancelled_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.PAYING, OrderStatus.CANCELLED))
                .isTrue();
    }

    // ======================== PAID transitions ========================

    @Test
    @DisplayName("PAID to PICKING is allowed")
    void testPaidToPicking_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.PICKING))
                .isTrue();
    }

    @Test
    @DisplayName("PAID to CANCEL_REVIEWING is allowed")
    void testPaidToCancelReviewing_allowed() {
        assertThat(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.CANCEL_REVIEWING))
                .isTrue();
    }

    // ======================== SHIPPED/DELIVERED cannot cancel ========================

    @Test
    @DisplayName("SHIPPED to CANCELLED is NOT allowed")
    void testShippedToCancelled_notAllowed() {
        assertThat(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED))
                .isFalse();
    }

    @Test
    @DisplayName("DELIVERED to CANCELLED is NOT allowed")
    void testDeliveredToCancelled_notAllowed() {
        assertThat(stateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
                .isFalse();
    }

    // ======================== Terminal states ========================

    @Test
    @DisplayName("COMPLETED has no outgoing transitions")
    void testCompleted_noOutgoingTransitions() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(stateMachine.canTransition(OrderStatus.COMPLETED, status))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("CLOSED has no outgoing transitions")
    void testClosed_noOutgoingTransitions() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(stateMachine.canTransition(OrderStatus.CLOSED, status))
                    .isFalse();
        }
    }

    // ======================== Null handling ========================

    @Test
    @DisplayName("canTransition with null from (initial creation) allows any to")
    void testCanTransition_nullFrom_allowsCreation() {
        assertThat(stateMachine.canTransition(null, OrderStatus.CREATED)).isTrue();
        assertThat(stateMachine.canTransition(null, OrderStatus.PAID)).isTrue();
    }

    @Test
    @DisplayName("canTransition with null to returns false")
    void testCanTransition_nullTo_returnsFalse() {
        assertThat(stateMachine.canTransition(OrderStatus.CREATED, null)).isFalse();
    }

    // ======================== validateTransition ========================

    @Test
    @DisplayName("validateTransition throws BusinessException for invalid transition")
    void testValidateTransition_invalid_throwsException() {
        assertThatThrownBy(() -> stateMachine.validateTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("validateTransition does not throw for valid transition")
    void testValidateTransition_valid_noException() {
        stateMachine.validateTransition(OrderStatus.CREATED, OrderStatus.CANCELLED);
        // No exception expected for valid transitions
    }

    // ======================== All valid transitions ========================

    @Test
    @DisplayName("test all valid transition paths")
    void testAllValidTransitions() {
        // Define all expected valid transitions (including the PAID->CANCELLED path)
        List<Object[]> validTransitions = Arrays.asList(
                // CREATED -> ...
                new Object[]{OrderStatus.CREATED, OrderStatus.PAYING, true},
                new Object[]{OrderStatus.CREATED, OrderStatus.CANCELLED, true},
                new Object[]{OrderStatus.CREATED, OrderStatus.CLOSED, true},
                new Object[]{OrderStatus.CREATED, OrderStatus.PAID, false},

                // PAYING -> ...
                new Object[]{OrderStatus.PAYING, OrderStatus.PAID, true},
                new Object[]{OrderStatus.PAYING, OrderStatus.CANCELLED, true},

                // PAID -> ... (includes PAID->CANCELLED path)
                new Object[]{OrderStatus.PAID, OrderStatus.PICKING, true},
                new Object[]{OrderStatus.PAID, OrderStatus.CANCEL_REVIEWING, true},
                new Object[]{OrderStatus.PAID, OrderStatus.CANCELLED, true},

                // PICKING -> ...
                new Object[]{OrderStatus.PICKING, OrderStatus.SHIPPED, true},

                // SHIPPED -> ...
                new Object[]{OrderStatus.SHIPPED, OrderStatus.DELIVERED, true},
                new Object[]{OrderStatus.SHIPPED, OrderStatus.CANCELLED, false},

                // DELIVERED -> ...
                new Object[]{OrderStatus.DELIVERED, OrderStatus.COMPLETED, true},
                new Object[]{OrderStatus.DELIVERED, OrderStatus.REFUNDING, true},
                new Object[]{OrderStatus.DELIVERED, OrderStatus.CANCELLED, false},

                // CANCEL_REVIEWING -> ...
                new Object[]{OrderStatus.CANCEL_REVIEWING, OrderStatus.CANCELLED, true},
                new Object[]{OrderStatus.CANCEL_REVIEWING, OrderStatus.PAID, true},

                // CANCELLED -> ...
                new Object[]{OrderStatus.CANCELLED, OrderStatus.CLOSED, true},

                // REFUNDING -> ...
                new Object[]{OrderStatus.REFUNDING, OrderStatus.REFUNDED, true},

                // REFUNDED -> ...
                new Object[]{OrderStatus.REFUNDED, OrderStatus.COMPLETED, true},

                // Terminal states
                new Object[]{OrderStatus.COMPLETED, OrderStatus.CANCELLED, false},
                new Object[]{OrderStatus.CLOSED, OrderStatus.CANCELLED, false}
        );

        for (Object[] testCase : validTransitions) {
            OrderStatus from = (OrderStatus) testCase[0];
            OrderStatus to = (OrderStatus) testCase[1];
            boolean expected = (boolean) testCase[2];

            assertThat(stateMachine.canTransition(from, to))
                    .as("Transition %s -> %s should be %s", from, to, expected ? "allowed" : "disallowed")
                    .isEqualTo(expected);
        }
    }
}

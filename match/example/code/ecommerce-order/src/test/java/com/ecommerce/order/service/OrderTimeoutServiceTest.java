package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OrderTimeoutService}.
 *
 * <p>When this service finds and cancels an expired order,
 * it only changes the order status to CANCELLED, but does NOT call
 * {@code InventoryReservationService.release()} to release the pre-occupied
 * inventory. The reserved stock remains occupied indefinitely.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutService")
class OrderTimeoutServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderTimeoutService orderTimeoutService;

    private Order expiredOrder;

    @BeforeEach
    void setUp() {
        expiredOrder = new Order();
        expiredOrder.setId(1L);
        expiredOrder.setOrderNo("SO202606070001");
        expiredOrder.setUserId(100L);
        expiredOrder.setStatus(OrderStatus.CREATED);
        expiredOrder.setPayableAmount(new BigDecimal("150.00"));
        expiredOrder.setExpiresAt(LocalDateTime.now().minusHours(1));
        // setCreatedAt to mock BaseEntity field — required for event recording
    }

    // ======================== timeout cancellation ========================

    @Test
    @DisplayName("timeout cancels expired order")
    void testTimeout_cancelsExpiredOrder() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.CREATED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredOrder));

        orderTimeoutService.cancelExpiredOrders();

        // Verify the order is marked as CANCELLED
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expiredOrder.getCancelReason()).contains("expired");

        // Verify cancel reason contains "60 minutes"
        assertThat(expiredOrder.getCancelReason()).contains("60 minutes");

        // No InventoryReservationService dependency exists in OrderTimeoutService.
        // The class has NO field of type InventoryReservationService, so release()
        // can NEVER be called. Let's verify by checking the constructor parameters.
        java.lang.reflect.Constructor<?>[] constructors = OrderTimeoutService.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        for (java.lang.reflect.Constructor<?> c : constructors) {
            for (Class<?> paramType : c.getParameterTypes()) {
                // Verify no InventoryReservationService parameter
                assertThat(paramType)
                        .as("OrderTimeoutService should NOT have InventoryReservationService as dependency")
                        .isNotEqualTo(com.ecommerce.inventory.query.InventoryReservationService.class);
            }
        }
    }

    @Test
    @DisplayName("timeout cancels order and publishes OrderCancelledEvent")
    void testTimeout_publishesOrderCancelledEvent() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.CREATED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredOrder));

        orderTimeoutService.cancelExpiredOrders();

        verify(eventPublisher).publish(any(com.ecommerce.order.event.OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("timeout records event log for cancelled order")
    void testTimeout_recordsEventLog() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.CREATED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredOrder));

        orderTimeoutService.cancelExpiredOrders();

        verify(orderService).recordEvent(
                eq(1L),
                eq(OrderStatus.CREATED),
                eq(OrderStatus.CANCELLED),
                eq("TIMEOUT_CANCEL"),
                eq("SYSTEM"),
                anyString());
    }

    @Test
    @DisplayName("timeout with no expired orders does nothing")
    void testTimeout_noExpiredOrders_doesNothing() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.CREATED), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        orderTimeoutService.cancelExpiredOrders();

        verify(orderService, never()).recordEvent(any(), any(), any(), anyString(), anyString(), anyString());
        verify(eventPublisher, never()).publish(any());
    }
}

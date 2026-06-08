package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.dto.BatchCreateOrderRequest;
import com.ecommerce.order.dto.BatchCreateOrderResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BatchOrderService}.
 *
 * <p>The {@link BatchOrderService#createBatch(Long, BatchCreateOrderRequest)}
 * method is annotated with {@code @Transactional}, meaning the ENTIRE batch runs
 * within a single database transaction. If any single order fails, all previously
 * created orders in that batch are rolled back, even when {@code continueOnError} is true.
 *
 * <p>The intended design should process each order in its own independent transaction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchOrderService")
class BatchOrderServiceTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private BatchOrderService batchOrderService;

    private CreateOrderRequest orderRequest1;
    private CreateOrderRequest orderRequest2;
    private CreateOrderResponse successResponse1;
    private CreateOrderResponse successResponse2;

    @BeforeEach
    void setUp() {
        // Build order request 1
        orderRequest1 = new CreateOrderRequest();
        orderRequest1.setAddressId(1L);
        orderRequest1.setExternalOrderNo("EXT-001");
        CreateOrderRequest.OrderItemRequest item1 = new CreateOrderRequest.OrderItemRequest();
        item1.setSkuId(10L);
        item1.setQuantity(2);
        orderRequest1.setItems(List.of(item1));

        // Build order request 2
        orderRequest2 = new CreateOrderRequest();
        orderRequest2.setAddressId(2L);
        orderRequest2.setExternalOrderNo("EXT-002");
        CreateOrderRequest.OrderItemRequest item2 = new CreateOrderRequest.OrderItemRequest();
        item2.setSkuId(20L);
        item2.setQuantity(1);
        orderRequest2.setItems(List.of(item2));

        // Build success response 1
        successResponse1 = new CreateOrderResponse();
        successResponse1.setOrderId(100L);
        successResponse1.setOrderNo("SO202606070100");
        successResponse1.setStatus("CREATED");
        successResponse1.setItemTotal(new BigDecimal("50.00"));
        successResponse1.setPayableAmount(new BigDecimal("52.00"));

        // Build success response 2
        successResponse2 = new CreateOrderResponse();
        successResponse2.setOrderId(200L);
        successResponse2.setOrderNo("SO202606070200");
        successResponse2.setStatus("CREATED");
        successResponse2.setItemTotal(new BigDecimal("30.00"));
        successResponse2.setPayableAmount(new BigDecimal("32.00"));
    }

    // ======================== Transactional rollback ========================

    @Test
    @DisplayName("single failure rolls back all orders when continueOnError=true")
    void testCreateBatch_oneFailure_rollsBackAll() {
        // The entire batch runs in one @Transactional, so a single failure
        // causes ALL previously created orders to be rolled back.
        // Even though continueOnError=true, the transaction is marked rollback-only.

        // Setup: first order succeeds, second order fails
        when(orderService.createOrder(eq(1L), any(CreateOrderRequest.class)))
                .thenReturn(successResponse1)
                .thenThrow(new RuntimeException("Order creation failed"));

        BatchCreateOrderRequest batchRequest = new BatchCreateOrderRequest();
        batchRequest.setOrders(Arrays.asList(orderRequest1, orderRequest2));
        batchRequest.setContinueOnError(true);

        BatchCreateOrderResponse response = batchOrderService.createBatch(1L, batchRequest);

        // Despite first order "succeeding", both orderService calls were made
        verify(orderService, times(2)).createOrder(eq(1L), any(CreateOrderRequest.class));

        // The response reports 1 success, 1 failure
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);

        // Because everything is in one transaction, the first
        // success would also be rolled back. In a real runtime, order 100 would
        // never be persisted. The continueOnError flag is meaningless.
        assertThat(response.getResults().get(0).isSuccess()).isTrue();
        assertThat(response.getResults().get(1).isSuccess()).isFalse();
        assertThat(response.getResults().get(1).getError()).contains("Order creation failed");
    }

    @Test
    @DisplayName("@Transactional annotation exists on BatchOrderService class")
    void testTransactionalAnnotation_present_onClass() {
        // Verify @Transactional is present on the class, which is the root cause of the single-transaction rollback behavior
        Transactional annotation = BatchOrderService.class.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("all orders succeed — all saved")
    void testCreateBatch_allSuccess_allSaved() {
        when(orderService.createOrder(eq(1L), any(CreateOrderRequest.class)))
                .thenReturn(successResponse1)
                .thenReturn(successResponse2);

        BatchCreateOrderRequest batchRequest = new BatchCreateOrderRequest();
        batchRequest.setOrders(Arrays.asList(orderRequest1, orderRequest2));
        batchRequest.setContinueOnError(true);

        BatchCreateOrderResponse response = batchOrderService.createBatch(1L, batchRequest);

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailureCount()).isEqualTo(0);

        assertThat(response.getResults().get(0).isSuccess()).isTrue();
        assertThat(response.getResults().get(0).getOrderId()).isEqualTo(100L);
        assertThat(response.getResults().get(1).isSuccess()).isTrue();
        assertThat(response.getResults().get(1).getOrderId()).isEqualTo(200L);

        verify(orderService, times(2)).createOrder(eq(1L), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("continueOnError=false: first failure rethrows and stops batch")
    void testCreateBatch_continueOnError_false_rethrows() {
        when(orderService.createOrder(eq(1L), any(CreateOrderRequest.class)))
                .thenReturn(successResponse1)
                .thenThrow(new RuntimeException("Order creation failed for EXT-002"));

        BatchCreateOrderRequest batchRequest = new BatchCreateOrderRequest();
        batchRequest.setOrders(Arrays.asList(orderRequest1, orderRequest2));
        batchRequest.setContinueOnError(false);

        assertThatThrownBy(() -> batchOrderService.createBatch(1L, batchRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Batch order processing aborted");
    }

    @Test
    @DisplayName("batch with single order succeeds")
    void testCreateBatch_singleOrder_success() {
        when(orderService.createOrder(eq(1L), any(CreateOrderRequest.class)))
                .thenReturn(successResponse1);

        BatchCreateOrderRequest batchRequest = new BatchCreateOrderRequest();
        batchRequest.setOrders(List.of(orderRequest1));
        batchRequest.setContinueOnError(true);

        BatchCreateOrderResponse response = batchOrderService.createBatch(1L, batchRequest);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(0);
    }
}

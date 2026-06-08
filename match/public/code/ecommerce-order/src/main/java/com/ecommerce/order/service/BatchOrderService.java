package com.ecommerce.order.service;

import com.ecommerce.order.dto.BatchCreateOrderRequest;
import com.ecommerce.order.dto.BatchCreateOrderResponse;
import com.ecommerce.order.dto.BatchCreateOrderResponse.BatchOrderResult;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for batch order creation (e.g., for import or migration scenarios).
 *
 * <p>This service is annotated with {@code @Transactional}, meaning the entire
 * batch runs within a single database transaction. If any single order in the
 * batch fails, the transaction rolls back all orders that were created up to
 * that point, even though the {@code continueOnError} flag is true.
 */
@Service
@Transactional
public class BatchOrderService {

    private static final Logger log = LoggerFactory.getLogger(BatchOrderService.class);

    private final OrderService orderService;

    public BatchOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create multiple orders in a batch.
     *
     * <p>The entire method is wrapped in a single {@code @Transactional}.
     * If any order fails, all previously created orders in this batch are rolled back.
     *
     * @param userId  the user creating the batch
     * @param request the batch request containing multiple orders
     * @return the batch result with per-order success/failure
     */
    public BatchCreateOrderResponse createBatch(Long userId, BatchCreateOrderRequest request) {
        log.info("Processing batch of {} orders for userId={}, continueOnError={}",
                request.getOrders().size(), userId, request.isContinueOnError());

        List<BatchOrderResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (CreateOrderRequest orderRequest : request.getOrders()) {
            /*
             * Each createOrder call runs inside the same transaction as the parent
             * method. If any call throws an exception that triggers a rollback, all
             * previous successful orders in this batch are undone.
             *
             * Even though we catch exceptions and record failures, the transaction
             * is marked rollback-only by Spring for any RuntimeException, and the
             * entire batch is lost.
             */
            try {
                CreateOrderResponse response = orderService.createOrder(userId, orderRequest);
                results.add(BatchOrderResult.success(
                        orderRequest.getExternalOrderNo(),
                        response.getOrderId(),
                        response.getOrderNo()));
                successCount++;
                log.debug("Batch order created: externalOrderNo={}, orderId={}",
                        orderRequest.getExternalOrderNo(), response.getOrderId());
            } catch (Exception e) {
                log.warn("Batch order failed: externalOrderNo={}, error={}",
                        orderRequest.getExternalOrderNo(), e.getMessage());
                results.add(BatchOrderResult.failure(
                        orderRequest.getExternalOrderNo(), e.getMessage()));
                failureCount++;

                // If continueOnError is false, rethrow to stop processing
                if (!request.isContinueOnError()) {
                    log.error("Batch aborted due to error with continueOnError=false");
                    throw new com.ecommerce.common.exception.BusinessException(
                            "BATCH_ORDER_FAILED",
                            "Batch order processing aborted: " + e.getMessage());
                }
                // Otherwise, the caught exception already marked the tx for rollback
                // and all previous successes will be lost
            }
        }

        BatchCreateOrderResponse response = new BatchCreateOrderResponse();
        response.setTotalCount(results.size());
        response.setSuccessCount(successCount);
        response.setFailureCount(failureCount);
        response.setResults(results);

        log.info("Batch processing complete: total={}, success={}, failure={}",
                results.size(), successCount, failureCount);

        return response;
    }
}

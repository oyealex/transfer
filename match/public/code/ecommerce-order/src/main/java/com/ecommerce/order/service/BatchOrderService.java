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

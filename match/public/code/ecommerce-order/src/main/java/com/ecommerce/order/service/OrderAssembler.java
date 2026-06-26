package com.ecommerce.order.service;

import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.dto.CreateOrderResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderDetailResponse.OrderEventLogDto;
import com.ecommerce.order.dto.OrderDetailResponse.OrderItemDto;
import com.ecommerce.order.dto.OrderListResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderEventLog;
import com.ecommerce.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles various order-related DTOs from entity objects.
 * Centralizes all DTO mapping logic for consistency and maintainability.
 */
@Component
public class OrderAssembler {

    /**
     * Build a CreateOrderResponse from an Order entity.
     */
    public CreateOrderResponse toCreateResponse(Order order) {
        CreateOrderResponse resp = new CreateOrderResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus().name());
        resp.setItemTotal(order.getItemTotal());
        resp.setShippingFee(order.getShippingFee());
        resp.setPackagingFee(order.getPackagingFee());
        resp.setDiscountAmount(order.getDiscountAmount());
        resp.setPointsDeductionAmount(order.getPointsDeductionAmount());
        resp.setPayableAmount(order.getPayableAmount());
        return resp;
    }

    /**
     * Build an OrderDetailResponse from Order, items, and event logs.
     */
    public OrderDetailResponse toDetailResponse(Order order, List<OrderItem> items,
                                                 List<OrderEventLog> logs) {
        OrderDetailResponse resp = new OrderDetailResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setUserId(order.getUserId());
        resp.setExternalOrderNo(order.getExternalOrderNo());
        resp.setStatus(order.getStatus().name());
        resp.setItemTotal(order.getItemTotal());
        resp.setShippingFee(order.getShippingFee());
        resp.setPackagingFee(order.getPackagingFee());
        resp.setDiscountAmount(order.getDiscountAmount());
        resp.setPointsDeductionAmount(order.getPointsDeductionAmount());
        resp.setPayableAmount(order.getPayableAmount());
        resp.setPaidAmount(order.getPaidAmount());
        resp.setAddressSnapshot(order.getAddressSnapshot());
        resp.setCouponIds(order.getCouponIds());
        resp.setRedeemedPoints(order.getRedeemedPoints());
        resp.setPaymentNo(order.getPaymentNo());
        resp.setCancelReason(order.getCancelReason());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setPaidAt(order.getPaidAt());
        resp.setCancelledAt(order.getCancelledAt());
        resp.setExpiresAt(order.getExpiresAt());

        if (items != null) {
            resp.setItems(items.stream().map(this::toItemDto).collect(Collectors.toList()));
        }
        if (logs != null) {
            resp.setEventLogs(logs.stream().map(this::toEventLogDto).collect(Collectors.toList()));
        }

        return resp;
    }

    /**
     * Build an OrderListResponse from an Order entity.
     */
    public OrderListResponse toListResponse(Order order, int itemCount) {
        OrderListResponse resp = new OrderListResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setExternalOrderNo(order.getExternalOrderNo());
        resp.setStatus(order.getStatus().name());
        resp.setItemTotal(order.getItemTotal());
        resp.setPayableAmount(order.getPayableAmount());
        resp.setItemCount(itemCount);
        resp.setCreatedAt(order.getCreatedAt());
        resp.setExpiresAt(order.getExpiresAt());
        return resp;
    }

    /**
     * Build a CancelOrderResponse from an Order entity and message.
     */
    public CancelOrderResponse toCancelResponse(Order order, String message) {
        return new CancelOrderResponse(
                order.getId(),
                order.getStatus().name(),
                message
        );
    }

    /**
     * Build an OrderItem DTO from an OrderItem entity.
     */
    public OrderItemDto toItemDto(OrderItem item) {
        OrderItemDto dto = new OrderItemDto();
        dto.setId(item.getId());
        dto.setOrderId(item.getOrderId());
        dto.setSkuId(item.getSkuId());
        dto.setSkuName(item.getSkuName());
        dto.setSkuCode(item.getSkuCode());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        dto.setSubtotal(item.getSubtotal());
        dto.setProductSnapshot(item.getProductSnapshot());
        return dto;
    }

    /**
     * Build an OrderEventLog DTO from an OrderEventLog entity.
     */
    public OrderEventLogDto toEventLogDto(OrderEventLog log) {
        OrderEventLogDto dto = new OrderEventLogDto();
        dto.setId(log.getId());
        dto.setOrderId(log.getOrderId());
        dto.setFromStatus(log.getFromStatus() != null ? log.getFromStatus().name() : null);
        dto.setToStatus(log.getToStatus().name());
        dto.setEventType(log.getEventType());
        dto.setOperatorId(log.getOperatorId());
        dto.setNote(log.getNote());
        dto.setCreatedAt(log.getCreatedAtLog());
        return dto;
    }
}

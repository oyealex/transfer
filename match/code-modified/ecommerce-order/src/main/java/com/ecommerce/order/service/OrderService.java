package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.loyalty.query.LoyaltyQueryService;
import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderDetailResponse.OrderEventLogDto;
import com.ecommerce.order.dto.OrderDetailResponse.OrderItemDto;
import com.ecommerce.order.dto.OrderListResponse;
import com.ecommerce.order.dto.VerifyPurchaseRequest;
import com.ecommerce.order.dto.VerifyPurchaseResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderEventLog;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.RiskCheckResult;
import com.ecommerce.order.event.OrderCancelledEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.repository.OrderEventLogRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.user.query.AddressDto;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core service for order creation, query, and cancellation.
 *
 * <p>This service orchestrates the full order creation flow:
 * <ol>
 *   <li>Validate preconditions</li>
 *   <li>Validate SKUs are available for sale</li>
 *   <li>Validate amounts</li>
 *   <li>Risk check</li>
 *   <li>Reserve inventory</li>
 *   <li>Calculate totals</li>
 *   <li>Calculate promotions and loyalty points</li>
 *   <li>Save order, items, and event log</li>
 *   <li>Publish OrderCreatedEvent</li>
 *   <li>Return response</li>
 * </ol>
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final UserQueryService userQueryService;
    private final ProductQueryService productQueryService;
    private final InventoryReservationService inventoryReservationService;
    private final LoyaltyQueryService loyaltyQueryService;
    private final OrderPreconditionChecker preconditionChecker;
    private final OrderValidator orderValidator;
    private final OrderTotalCalculator totalCalculator;
    private final OrderStateMachine stateMachine;
    private final OrderRiskChecker riskChecker;
    private final DomainEventPublisher eventPublisher;
    private final com.ecommerce.promotion.service.PromotionCalculationService promotionCalculationService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        OrderEventLogRepository orderEventLogRepository,
                        UserQueryService userQueryService,
                        ProductQueryService productQueryService,
                        InventoryReservationService inventoryReservationService,
                        LoyaltyQueryService loyaltyQueryService,
                        OrderPreconditionChecker preconditionChecker,
                        OrderValidator orderValidator,
                        OrderTotalCalculator totalCalculator,
                        OrderStateMachine stateMachine,
                        OrderRiskChecker riskChecker,
                        DomainEventPublisher eventPublisher,
                        com.ecommerce.promotion.service.PromotionCalculationService promotionCalculationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderEventLogRepository = orderEventLogRepository;
        this.userQueryService = userQueryService;
        this.productQueryService = productQueryService;
        this.inventoryReservationService = inventoryReservationService;
        this.loyaltyQueryService = loyaltyQueryService;
        this.preconditionChecker = preconditionChecker;
        this.orderValidator = orderValidator;
        this.totalCalculator = totalCalculator;
        this.stateMachine = stateMachine;
        this.riskChecker = riskChecker;
        this.eventPublisher = eventPublisher;
        this.promotionCalculationService = promotionCalculationService;
    }

    /**
     * Create a new order with the full business flow.
     *
     * @param userId  the authenticated user ID
     * @param request the order creation request
     * @return the created order response
     */
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        log.info("Creating order for userId={}, itemsCount={}", userId,
                request.getItems() != null ? request.getItems().size() : 0);

        // ===== Step 1: Validate preconditions =====
        // Checks user existence only (does not check frozen status)
        preconditionChecker.check(userId, request.getItems().size());

        // Pre-check: if all quantities are zero or negative, total will be zero => invalid amount
        boolean hasPositiveQuantity = request.getItems().stream()
                .anyMatch(item -> item.getQuantity() > 0);
        if (!hasPositiveQuantity) {
            throw new OrderValidationException("Order amount must be positive, all item quantities are zero or negative");
        }

        // ===== Step 2: Validate SKUs and get product data =====
        List<CreateOrderRequest.OrderItemRequest> requestItems = request.getItems();
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal itemTotal = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest reqItem : requestItems) {
            // Validate quantity
            orderValidator.validateQuantity(reqItem.getQuantity());

            // Get SKU for sale (throws if not available)
            SkuDto sku = productQueryService.getSkuForSale(reqItem.getSkuId());

            // Create order item
            OrderItem orderItem = new OrderItem();
            orderItem.setSkuId(sku.getSkuId());
            orderItem.setSkuName(sku.getName());
            orderItem.setSkuCode(sku.getSkuCode());
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(reqItem.getQuantity());
            orderItem.setSubtotal(MonetaryUtil.multiply(sku.getPrice(),
                    BigDecimal.valueOf(reqItem.getQuantity())));
            orderItems.add(orderItem);

            itemTotal = MonetaryUtil.add(itemTotal, orderItem.getSubtotal());
        }

        // ===== Step 3: Validate calculated amounts =====
        orderValidator.validateAmount(itemTotal);

        // ===== Step 4: Risk check =====
        RiskCheckResult riskResult = riskChecker.check(userId, itemTotal, orderItems.stream()
                .map(OrderItem::getSkuId)
                .collect(java.util.stream.Collectors.toList()));
        if (!riskResult.isPassed()) {
            throw new BusinessException("ORDER_RISK_REJECTED",
                    "Order rejected by risk control: " + riskResult.getReason());
        }

        // ===== Step 5: Calculate shipping and packaging fees =====
        BigDecimal shippingFee = totalCalculator.calculateShippingFee(itemTotal);
        BigDecimal packagingFee = totalCalculator.calculatePackagingFee(orderItems.size());

        // ===== Step 6: Calculate promotions and discounts =====
        BigDecimal discountAmount = calculateDiscounts(userId, request, orderItems, itemTotal);

        // ===== Step 7: Calculate loyalty points deduction =====
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;
        int redeemedPoints = 0;
        if (request.getRedeemPoints() > 0) {
            // Need a preliminary payable amount for points estimation
            BigDecimal prePointsAmount = MonetaryUtil.add(itemTotal, packagingFee);
            prePointsAmount = MonetaryUtil.subtract(prePointsAmount, discountAmount);

            int redeemable = loyaltyQueryService.estimateRedeemPoints(prePointsAmount, userId);
            redeemedPoints = Math.min(request.getRedeemPoints(), redeemable);

            if (redeemedPoints > 0) {
                // 100 points = 1 yuan
                pointsDeductionAmount = MonetaryUtil.multiply(
                        BigDecimal.valueOf(redeemedPoints), new BigDecimal("0.01"));
            }
        }

        // ===== Step 8: Calculate final payable amount =====
        BigDecimal payableAmount = totalCalculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        // Validate final payable amount
        orderValidator.validateAmount(payableAmount);

        // ===== Step 9: Create and save the order =====
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setExternalOrderNo(request.getExternalOrderNo());
        order.setStatus(OrderStatus.CREATED);
        order.setItemTotal(itemTotal);
        order.setShippingFee(shippingFee);
        order.setPackagingFee(packagingFee);
        order.setDiscountAmount(discountAmount);
        order.setPointsDeductionAmount(pointsDeductionAmount);
        order.setPayableAmount(payableAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setRedeemedPoints(redeemedPoints);
        order.setExpiresAt(SystemClockService.now().plusMinutes(60));

        // Snapshot address
        if (request.getAddressId() != null) {
            AddressDto address = userQueryService.getDefaultAddress(userId);
            if (address != null) {
                order.setAddressSnapshot(toAddressSnapshot(address));
            }
        }

        // Snapshot coupon IDs
        if (request.getCouponIds() != null && !request.getCouponIds().isEmpty()) {
            order.setCouponIds(request.getCouponIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }

        order = orderRepository.save(order);

        // Set orderId on items and save them
        final Long orderId = order.getId();
        for (OrderItem item : orderItems) {
            item.setOrderId(orderId);
            // Snapshot the product data for historical accuracy
            try {
                com.ecommerce.product.query.ProductSnapshotDto snapshot =
                        productQueryService.getProductSnapshot(item.getSkuId());
                item.setProductSnapshot(toProductSnapshotJson(snapshot));
            } catch (Exception e) {
                log.warn("Failed to create product snapshot for skuId={}: {}", item.getSkuId(), e.getMessage());
            }
        }
        orderItemRepository.saveAll(orderItems);

        // Record initial event log
        recordEvent(orderId, null, OrderStatus.CREATED, "CREATE",
                userId.toString(), "Order created");

        // ===== Step 10: Reserve inventory =====
        List<ReserveItem> reserveItems = orderItems.stream()
                .map(item -> new ReserveItem(item.getSkuId(), item.getQuantity()))
                .collect(Collectors.toList());
        inventoryReservationService.reserve(orderId, reserveItems);

        // ===== Step 11: Publish OrderCreatedEvent =====
        eventPublisher.publish(new OrderCreatedEvent(this, orderId, userId, payableAmount));

        log.info("Order created successfully: orderId={}, orderNo={}, payableAmount={}",
                orderId, order.getOrderNo(), payableAmount);

        // ===== Step 12: Build and return response =====
        return buildCreateResponse(order);
    }

    /**
     * Get order detail by order ID.
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<OrderEventLog> logs = orderEventLogRepository.findByOrderIdOrderByCreatedAtLogAsc(orderId);

        return buildDetailResponse(order, items, logs);
    }

    /**
     * Get order by its unique order number.
     */
    @Transactional(readOnly = true)
    public Order getByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNo));
    }

    /**
     * List orders for a user, paginated.
     */
    @Transactional(readOnly = true)
    public Page<OrderListResponse> listUserOrders(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findByUserId(userId, pageable);

        return orderPage.map(order -> {
            OrderListResponse resp = new OrderListResponse();
            resp.setOrderId(order.getId());
            resp.setOrderNo(order.getOrderNo());
            resp.setExternalOrderNo(order.getExternalOrderNo());
            resp.setStatus(order.getStatus().name());
            resp.setLogisticsStatus(order.getLogisticsStatus());
            resp.setItemTotal(order.getItemTotal());
            resp.setPayableAmount(order.getPayableAmount());
            resp.setCreatedAt(order.getCreatedAt());
            resp.setExpiresAt(order.getExpiresAt());

            // Get item count
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            resp.setItemCount(items.size());

            return resp;
        });
    }

    /**
     * Verify that a user has purchased and received a product.
     */
    @Transactional(readOnly = true)
    public VerifyPurchaseResponse verifyPurchase(VerifyPurchaseRequest request) {
        // Find delivered orders for the user
        Page<Order> orders = orderRepository.findByUserId(request.getUserId(),
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "deliveredAt")));

        for (Order order : orders) {
            // Only consider DELIVERED or COMPLETED orders
            if (order.getStatus() != OrderStatus.DELIVERED
                    && order.getStatus() != OrderStatus.COMPLETED) {
                continue;
            }
            // Check if any item in this order matches the product (SPU) ID
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : items) {
                SkuDto sku = productQueryService.getSku(item.getSkuId());
                if (sku != null && sku.getSpuId().equals(request.getProductId())) {
                    return new VerifyPurchaseResponse(true, order.getId(), order.getUpdatedAt());
                }
            }
        }
        return new VerifyPurchaseResponse(false, null, null);
    }

    /**
     * Record a status transition event.
     */
    public void recordEvent(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
                            String eventType, String operatorId, String note) {
        OrderEventLog logEntry = new OrderEventLog();
        logEntry.setOrderId(orderId);
        logEntry.setFromStatus(fromStatus);
        logEntry.setToStatus(toStatus);
        logEntry.setEventType(eventType);
        logEntry.setOperatorId(operatorId);
        logEntry.setNote(note);
        logEntry.setCreatedAtLog(LocalDateTime.now());
        orderEventLogRepository.save(logEntry);
    }

    // ======================== Private helpers ========================

    /**
     * Generate a unique order number: SO + yyyyMMdd + 4-digit sequence.
     * In production this would use a database sequence or distributed ID generator.
     */
    private String generateOrderNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Use a simple timestamp-based suffix for uniqueness
        String seqPart = String.format("%04d", System.currentTimeMillis() % 10000);
        return "SO" + datePart + seqPart;
    }

    /**
     * Calculate applicable discounts via the promotion module.
     */
    private BigDecimal calculateDiscounts(Long userId, CreateOrderRequest request,
                                          List<OrderItem> orderItems, BigDecimal itemTotal) {
        try {
            List<com.ecommerce.promotion.dto.PromotionCalculateRequest.CalculateItem> calcItems =
                    new ArrayList<>();
            for (OrderItem item : orderItems) {
                com.ecommerce.promotion.dto.PromotionCalculateRequest.CalculateItem calcItem =
                        new com.ecommerce.promotion.dto.PromotionCalculateRequest.CalculateItem();
                calcItem.setSkuId(item.getSkuId());
                calcItem.setPrice(item.getPrice());
                calcItem.setQuantity(item.getQuantity());
                calcItems.add(calcItem);
            }

            com.ecommerce.promotion.dto.PromotionCalculateRequest calcRequest =
                    new com.ecommerce.promotion.dto.PromotionCalculateRequest();
            calcRequest.setUserId(userId);
            calcRequest.setItems(calcItems);
            calcRequest.setCouponIds(request.getCouponIds() != null
                    ? request.getCouponIds() : Collections.emptyList());

            com.ecommerce.promotion.dto.PromotionCalculateResponse calcResponse =
                    promotionCalculationService.calculate(calcRequest);

            return calcResponse.getTotalDiscount();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to calculate promotions, using zero discount: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String toAddressSnapshot(AddressDto address) {
        return "{" +
                "\"province\":\"" + escape(address.getProvince()) + "\"," +
                "\"city\":\"" + escape(address.getCity()) + "\"," +
                "\"district\":\"" + escape(address.getDistrict()) + "\"," +
                "\"detail\":\"" + escape(address.getDetail()) + "\"," +
                "\"receiverName\":\"" + escape(address.getReceiverName()) + "\"," +
                "\"receiverPhone\":\"" + escape(address.getReceiverPhone()) + "\"" +
                "}";
    }

    private String toProductSnapshotJson(com.ecommerce.product.query.ProductSnapshotDto snapshot) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"skuId\":").append(snapshot.getSkuId()).append(",");
        sb.append("\"name\":\"").append(escape(snapshot.getName())).append("\",");
        sb.append("\"price\":").append(snapshot.getPrice());
        if (snapshot.getImage() != null) {
            sb.append(",\"image\":\"").append(escape(snapshot.getImage())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private CreateOrderResponse buildCreateResponse(Order order) {
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

    private OrderDetailResponse buildDetailResponse(Order order,
                                                     List<OrderItem> items,
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
        resp.setPaymentStatus(order.getPaymentNo() != null ? "PAID" : null);
        resp.setLogisticsStatus(order.getLogisticsStatus());
        resp.setCancelReason(order.getCancelReason());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setPaidAt(order.getPaidAt());
        resp.setCancelledAt(order.getCancelledAt());
        resp.setExpiresAt(order.getExpiresAt());

        // Map items
        List<OrderItemDto> itemDtos = items.stream().map(item -> {
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
        }).collect(Collectors.toList());
        resp.setItems(itemDtos);

        // Map event logs
        List<OrderEventLogDto> logDtos = logs.stream().map(log -> {
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
        }).collect(Collectors.toList());
        resp.setEventLogs(logDtos);

        return resp;
    }
}

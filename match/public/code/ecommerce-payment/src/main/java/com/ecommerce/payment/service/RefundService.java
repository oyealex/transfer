package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.payment.dto.RefundApplyRequest;
import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.dto.RefundReviewRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.event.RefundCompletedEvent;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the refund lifecycle: application, review, warehouse acceptance, and completion.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRecordRepository refundRecordRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundCalculator refundCalculator;
    private final DomainEventPublisher eventPublisher;
    private final LocalNotificationService notificationService;

    public RefundService(RefundRecordRepository refundRecordRepository,
                         PaymentRecordRepository paymentRecordRepository,
                         RefundCalculator refundCalculator,
                         DomainEventPublisher eventPublisher,
                         LocalNotificationService notificationService) {
        this.refundRecordRepository = refundRecordRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundCalculator = refundCalculator;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    /**
     * Applies for a refund.
     */
    @Transactional
    public RefundResponse applyRefund(Long userId, RefundApplyRequest request) {
        log.info("Applying refund: userId={}, orderId={}, paymentNo={}",
                userId, request.getOrderId(), request.getPaymentNo());

        // Find the successful payment
        PaymentRecord payment = paymentRecordRepository
                .findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PaymentRecord", request.getPaymentNo()));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException("REFUND_NOT_ALLOWED",
                    "Refund can only be applied for successfully paid orders");
        }

        // Calculate refund amount
        BigDecimal refundAmount = refundCalculator.calculate(payment.getPaidAmount());

        // Create refund record
        RefundRecord refund = new RefundRecord();
        refund.setRefundNo(generateRefundNo());
        refund.setPaymentNo(request.getPaymentNo());
        refund.setOrderId(request.getOrderId());
        refund.setUserId(userId);
        refund.setRefundAmount(refundAmount);
        refund.setReason(request.getReason());
        refund.setStatus(RefundStatus.PENDING_REVIEW);

        refund = refundRecordRepository.save(refund);

        log.info("Refund applied: refundNo={}, amount={}", refund.getRefundNo(), refund.getRefundAmount());

        return toRefundResponse(refund);
    }

    /**
     * Admin reviews a refund application.
     */
    @Transactional
    public RefundResponse reviewRefund(Long refundId, Long reviewerId, RefundReviewRequest request) {
        log.info("Reviewing refund: refundId={}, reviewerId={}, approved={}",
                refundId, reviewerId, request.isApproved());

        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));

        if (refund.getStatus() != RefundStatus.PENDING_REVIEW) {
            throw new BusinessException("REFUND_STATUS_INVALID",
                    "Refund is not in PENDING_REVIEW status: " + refund.getStatus());
        }

        if (request.isApproved()) {
            approveRefund(refund.getId(), reviewerId, request.getNote());
        } else {
            refund.setStatus(RefundStatus.REJECTED);
            refund.setReviewerId(reviewerId);
            refund.setReviewNote(request.getNote());
            refundRecordRepository.save(refund);

            log.info("Refund rejected: refundNo={}", refund.getRefundNo());
        }

        return toRefundResponse(refund);
    }

    /**
     * Approves a refund.
     */
    private void approveRefund(Long refundId, Long reviewerId, String note) {
        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));

        refund.setStatus(RefundStatus.APPROVED);
        refund.setReviewerId(reviewerId);
        refund.setReviewNote(note);
        refundRecordRepository.save(refund);

        processRefund(refund);
    }

    /**
     * Warehouse accepts returned goods.
     */
    @Transactional
    public RefundResponse warehouseAccept(Long refundId, Long acceptorId) {
        log.info("Warehouse accepting refund: refundId={}, acceptorId={}", refundId, acceptorId);

        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));

        if (refund.getStatus() != RefundStatus.WAITING_WAREHOUSE_ACCEPT) {
            throw new BusinessException("REFUND_STATUS_INVALID",
                    "Refund must be WAITING_WAREHOUSE_ACCEPT to accept, current: "
                            + refund.getStatus());
        }

        refund.setStatus(RefundStatus.WAREHOUSE_ACCEPTED);
        refund.setWarehouseAcceptorId(acceptorId);
        refund = refundRecordRepository.save(refund);

        // After warehouse acceptance, process the refund
        processRefund(refund);

        return toRefundResponse(refund);
    }

    /**
     * Processes the refund completion.
     */
    private void processRefund(RefundRecord refund) {
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(LocalDateTime.now());
        refundRecordRepository.save(refund);

        // Update payment status
        PaymentRecord payment = paymentRecordRepository
                .findByPaymentNo(refund.getPaymentNo())
                .orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRecordRepository.save(payment);

        // Publish event — order module will consume RefundCompletedEvent
        // and update the order status to REFUNDED via its own listener
        RefundCompletedEvent event = new RefundCompletedEvent(
                this, refund.getRefundNo(), refund.getPaymentNo(),
                refund.getOrderId(), refund.getUserId(), refund.getRefundAmount());
        eventPublisher.publish(event);

        // Send notification
        sendRefundNotification(refund);

        log.info("Refund completed: refundNo={}, amount={}", refund.getRefundNo(), refund.getRefundAmount());
    }

    /**
     * Gets a refund by ID.
     */
    public RefundResponse getRefund(Long refundId) {
        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));
        return toRefundResponse(refund);
    }

    private void sendRefundNotification(RefundRecord refund) {
        NotificationRequest request = new NotificationRequest();
        request.setBizType("REFUND_COMPLETED");
        request.setBizId(refund.getRefundNo());
        request.setChannel(NotificationChannel.EMAIL);
        request.setTemplateCode("refund_completed");
        request.setVariables(Map.of(
                "refundNo", refund.getRefundNo(),
                "amount", refund.getRefundAmount().toString()
        ));
        request.setIdempotencyKey("refund_notify_" + refund.getRefundNo());
        notificationService.send(request);
    }

    private String generateRefundNo() {
        return "RF" + System.currentTimeMillis() + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private RefundResponse toRefundResponse(RefundRecord refund) {
        RefundResponse response = new RefundResponse();
        response.setId(refund.getId());
        response.setRefundNo(refund.getRefundNo());
        response.setPaymentNo(refund.getPaymentNo());
        response.setOrderId(refund.getOrderId());
        response.setUserId(refund.getUserId());
        response.setRefundAmount(refund.getRefundAmount());
        response.setReason(refund.getReason());
        response.setStatus(refund.getStatus());
        response.setReviewNote(refund.getReviewNote());
        response.setCompletedAt(refund.getCompletedAt());
        response.setCreatedAt(refund.getCreatedAt());
        return response;
    }
}

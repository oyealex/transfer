package com.ecommerce.order.integration;

import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for sending order-related notifications to users.
 *
 * <p>This service wraps the LocalNotificationService from the common module
 * and provides order-specific notification building, including:
 * <ul>
 *   <li>Order confirmation (creation) notification</li>
 *   <li>Payment success notification</li>
 *   <li>Shipping notification</li>
 *   <li>Delivery confirmation notification</li>
 *   <li>Order cancellation notification</li>
 *   <li>Order expiry warning notification</li>
 * </ul>
 *
 * <p>Notifications are sent through the LocalNotificationService which may
 * dispatch via email, SMS, or push notification depending on user preferences.
 */
@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    private final LocalNotificationService notificationService;

    public OrderNotificationService(LocalNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Send order creation confirmation notification.
     */
    public void notifyOrderCreated(Order order, String userEmail) {
        try {
            String message = buildOrderConfirmationMessage(order);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Order Confirmed: " + order.getOrderNo())
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Order confirmation notification sent for order {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send order confirmation notification: {}", e.getMessage());
        }
    }

    /**
     * Send payment success notification.
     */
    public void notifyPaymentSuccess(Order order, String userEmail) {
        try {
            String message = buildPaymentSuccessMessage(order);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Payment Confirmed: " + order.getOrderNo())
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Payment success notification sent for order {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send payment success notification: {}", e.getMessage());
        }
    }

    /**
     * Send shipping notification with tracking info.
     */
    public void notifyOrderShipped(Order order, String trackingNumber, String userEmail) {
        try {
            String message = buildShippingMessage(order, trackingNumber);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Your Order Has Shipped: " + order.getOrderNo())
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Shipping notification sent for order {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send shipping notification: {}", e.getMessage());
        }
    }

    /**
     * Send delivery confirmation notification.
     */
    public void notifyOrderDelivered(Order order, String userEmail) {
        try {
            String message = buildDeliveryMessage(order);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Your Order Has Been Delivered: " + order.getOrderNo())
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Delivery notification sent for order {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send delivery notification: {}", e.getMessage());
        }
    }

    /**
     * Send order cancellation notification.
     */
    public void notifyOrderCancelled(Order order, String reason, String userEmail) {
        try {
            String message = buildCancellationMessage(order, reason);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Order Cancelled: " + order.getOrderNo())
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Cancellation notification sent for order {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send cancellation notification: {}", e.getMessage());
        }
    }

    /**
     * Send payment expiring soon warning.
     */
    public void notifyPaymentExpiring(Order order, String userEmail, long minutesRemaining) {
        try {
            String message = buildExpiryWarningMessage(order, minutesRemaining);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Payment Expiring Soon: " + order.getOrderNo())
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Payment expiry warning sent for order {} ({} minutes remaining)",
                    order.getOrderNo(), minutesRemaining);
        } catch (Exception e) {
            log.warn("Failed to send payment expiry warning: {}", e.getMessage());
        }
    }

    /**
     * Send order status update notification.
     */
    public void notifyStatusUpdate(Order order, OrderStatus newStatus, String userEmail) {
        try {
            String message = buildStatusUpdateMessage(order, newStatus);
            NotificationRequest request = NotificationRequest.builder()
                    .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                    .recipient(userEmail)
                    .subject("Order Update: " + order.getOrderNo()
                            + " is now " + newStatus)
                    .content(message)
                    .build();
            notificationService.send(request);
            log.debug("Status update notification sent for order {}: {}", order.getOrderNo(), newStatus);
        } catch (Exception e) {
            log.warn("Failed to send status update notification: {}", e.getMessage());
        }
    }

    /**
     * Send batch notification to multiple recipients.
     */
    public void notifyBatch(List<Order> orders, String template, String userEmail) {
        for (Order order : orders) {
            try {
                NotificationRequest request = NotificationRequest.builder()
                        .channel(com.ecommerce.common.notification.NotificationChannel.EMAIL)
                        .recipient(userEmail)
                        .subject("Batch Update for Order " + order.getOrderNo())
                        .content(template.replace("{{orderNo}}", order.getOrderNo())
                                .replace("{{status}}", order.getStatus().name()))
                        .build();
                notificationService.send(request);
            } catch (Exception e) {
                log.warn("Failed to send batch notification for order {}: {}",
                        order.getOrderNo(), e.getMessage());
            }
        }
    }

    // ======================== Message builders ========================

    private String buildOrderConfirmationMessage(Order order) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return "Dear Customer,\n\n"
                + "Your order has been created successfully.\n\n"
                + "Order Number: " + order.getOrderNo() + "\n"
                + "Order Amount: $" + order.getPayableAmount() + "\n"
                + "Order Time: " + (order.getCreatedAt() != null
                        ? order.getCreatedAt().format(dtf) : "N/A") + "\n\n"
                + "Please complete payment within 60 minutes.\n"
                + "Expires At: " + (order.getExpiresAt() != null
                        ? order.getExpiresAt().format(dtf) : "N/A") + "\n\n"
                + "Thank you for shopping with us!";
    }

    private String buildPaymentSuccessMessage(Order order) {
        return "Dear Customer,\n\n"
                + "Your payment has been confirmed.\n\n"
                + "Order Number: " + order.getOrderNo() + "\n"
                + "Amount Paid: $" + order.getPaidAmount() + "\n"
                + "Payment No: " + (order.getPaymentNo() != null ? order.getPaymentNo() : "N/A") + "\n\n"
                + "We will notify you when your order ships.";
    }

    private String buildShippingMessage(Order order, String trackingNumber) {
        return "Dear Customer,\n\n"
                + "Your order has been shipped!\n\n"
                + "Order Number: " + order.getOrderNo() + "\n"
                + "Tracking Number: " + trackingNumber + "\n\n"
                + "Your order is on its way.";
    }

    private String buildDeliveryMessage(Order order) {
        return "Dear Customer,\n\n"
                + "Your order has been delivered.\n\n"
                + "Order Number: " + order.getOrderNo() + "\n\n"
                + "If you have any issues, please contact customer service.\n"
                + "Thank you for your purchase!";
    }

    private String buildCancellationMessage(Order order, String reason) {
        return "Dear Customer,\n\n"
                + "Your order has been cancelled.\n\n"
                + "Order Number: " + order.getOrderNo() + "\n"
                + "Reason: " + (reason != null ? reason : "User requested") + "\n\n"
                + "If this was a mistake, please place a new order.\n"
                + "We apologize for any inconvenience.";
    }

    private String buildExpiryWarningMessage(Order order, long minutesRemaining) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return "Dear Customer,\n\n"
                + "Your order payment is expiring soon.\n\n"
                + "Order Number: " + order.getOrderNo() + "\n"
                + "Amount: $" + order.getPayableAmount() + "\n"
                + "Time Remaining: " + minutesRemaining + " minutes\n"
                + "Expires At: " + (order.getExpiresAt() != null
                        ? order.getExpiresAt().format(dtf) : "N/A") + "\n\n"
                + "Please complete your payment to avoid order cancellation.";
    }

    private String buildStatusUpdateMessage(Order order, OrderStatus newStatus) {
        return "Dear Customer,\n\n"
                + "Your order status has been updated.\n\n"
                + "Order Number: " + order.getOrderNo() + "\n"
                + "New Status: " + newStatus + "\n\n"
                + "Log in to view your order details.";
    }
}

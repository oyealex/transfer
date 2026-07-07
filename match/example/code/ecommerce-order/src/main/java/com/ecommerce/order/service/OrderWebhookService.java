package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for sending webhook notifications to external systems when
 * order statuses change. Supports registering callback URLs and retry logic.
 *
 * <p>Used by external ERP, warehouse management, and analytics systems
 * that need real-time order status updates.
 */
@Service
public class OrderWebhookService {

    private static final Logger log = LoggerFactory.getLogger(OrderWebhookService.class);

    private final Map<String, List<String>> registeredWebhooks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final HttpClient httpClient;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private static final int MAX_RETRIES = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public OrderWebhookService(OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Register a webhook URL for a specific event type.
     *
     * @param eventType the event type (e.g., "ORDER_CREATED", "ORDER_PAID")
     * @param url       the callback URL
     */
    public void registerWebhook(String eventType, String url) {
        registeredWebhooks.computeIfAbsent(eventType, k -> new java.util.ArrayList<>()).add(url);
        log.info("Registered webhook for event={}, url={}", eventType, url);
    }

    /**
     * Unregister a webhook URL.
     */
    public void unregisterWebhook(String eventType, String url) {
        List<String> urls = registeredWebhooks.get(eventType);
        if (urls != null) {
            urls.remove(url);
            log.info("Unregistered webhook for event={}, url={}", eventType, url);
        }
    }

    /**
     * Notify all registered webhooks for an order status change.
     * Runs asynchronously to not block the main business transaction.
     *
     * @param eventType the event type
     * @param orderId   the order ID
     */
    public void notifyWebhooks(String eventType, Long orderId) {
        List<String> urls = registeredWebhooks.get(eventType);
        if (urls == null || urls.isEmpty()) {
            return;
        }

        // Build the payload once
        String payload = buildWebhookPayload(orderId, eventType);
        if (payload == null) {
            return;
        }

        // Send to all registered URLs asynchronously
        for (String url : urls) {
            executor.submit(() -> sendWithRetry(url, payload, eventType, 0));
        }
    }

    /**
     * Build the JSON payload for a webhook notification.
     */
    private String buildWebhookPayload(Long orderId, String eventType) {
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) return null;

            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"event\": \"").append(eventType).append("\",\n");
            sb.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
            sb.append("  \"order\": {\n");
            sb.append("    \"orderId\": ").append(order.getId()).append(",\n");
            sb.append("    \"orderNo\": \"").append(order.getOrderNo()).append("\",\n");
            sb.append("    \"userId\": ").append(order.getUserId()).append(",\n");
            sb.append("    \"status\": \"").append(order.getStatus()).append("\",\n");
            sb.append("    \"itemTotal\": ").append(order.getItemTotal()).append(",\n");
            sb.append("    \"shippingFee\": ").append(order.getShippingFee()).append(",\n");
            sb.append("    \"packagingFee\": ").append(order.getPackagingFee()).append(",\n");
            sb.append("    \"discountAmount\": ").append(order.getDiscountAmount()).append(",\n");
            sb.append("    \"pointsDeductionAmount\": ").append(order.getPointsDeductionAmount()).append(",\n");
            sb.append("    \"payableAmount\": ").append(order.getPayableAmount()).append(",\n");
            sb.append("    \"paidAmount\": ").append(
                    order.getPaidAmount() != null ? order.getPaidAmount().toString() : "null").append(",\n");
            sb.append("    \"paymentNo\": ").append(
                    order.getPaymentNo() != null ? "\"" + order.getPaymentNo() + "\"" : "null").append(",\n");
            sb.append("    \"createdAt\": \"").append(order.getCreatedAt()).append("\",\n");
            sb.append("    \"expiresAt\": \"").append(order.getExpiresAt()).append("\",\n");
            sb.append("    \"items\": [\n");
            for (int i = 0; i < items.size(); i++) {
                OrderItem item = items.get(i);
                sb.append("      {\"skuId\": ").append(item.getSkuId());
                sb.append(", \"skuName\": \"").append(escapeJson(item.getSkuName())).append("\"");
                sb.append(", \"price\": ").append(item.getPrice());
                sb.append(", \"quantity\": ").append(item.getQuantity());
                sb.append(", \"subtotal\": ").append(item.getSubtotal()).append("}");
                if (i < items.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("    ]\n");
            sb.append("  }\n");
            sb.append("}");

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build webhook payload for order {}: {}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * Send with retry logic.
     */
    private void sendWithRetry(String url, String payload, String eventType, int attempt) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event", eventType)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Webhook delivered to {} (status={})", url, response.statusCode());
            } else {
                log.warn("Webhook to {} returned status={} (attempt {}/{})",
                        url, response.statusCode(), attempt + 1, MAX_RETRIES + 1);
                retry(url, payload, eventType, attempt);
            }
        } catch (Exception e) {
            log.warn("Webhook delivery failed to {} (attempt {}/{}): {}",
                    url, attempt + 1, MAX_RETRIES + 1, e.getMessage());
            retry(url, payload, eventType, attempt);
        }
    }

    private void retry(String url, String payload, String eventType, int attempt) {
        if (attempt < MAX_RETRIES) {
            executor.submit(() -> {
                try {
                    Thread.sleep(1000L * (attempt + 1)); // Exponential-ish backoff
                    sendWithRetry(url, payload, eventType, attempt + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            log.error("Webhook permanently failed after {} attempts: event={}, url={}",
                    MAX_RETRIES + 1, eventType, url);
        }
    }

    /**
     * Get all registered webhooks.
     */
    public Map<String, List<String>> getRegisteredWebhooks() {
        return new ConcurrentHashMap<>(registeredWebhooks);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}

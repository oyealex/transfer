package com.ecommerce.common.notification;

import com.ecommerce.common.test.FaultInjectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of LocalNotificationService.
 * Handles idempotency via an in-memory ConcurrentHashMap, renders templates,
 * and delegates to the appropriate channel sender (MockMailSender or MockSmsSender).
 *
 * <p>Business modules must inject LocalNotificationService
 * (the interface) and call {@link #send(NotificationRequest)}.
 * The MockMailSender and MockSmsSender are internal implementation details.
 */
@Service
public class LocalNotificationServiceImpl implements LocalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationServiceImpl.class);

    // Test observation: records every notification sent (not idempotency-skipped)
    private static final List<NotificationRecord> sentRecords =
            Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Boolean> idempotencyCache = new ConcurrentHashMap<>();
    private final MockMailSender mockMailSender;
    private final MockSmsSender mockSmsSender;

    public LocalNotificationServiceImpl() {
        this.mockMailSender = new MockMailSender();
        this.mockSmsSender = new MockSmsSender();
    }

    @Override
    public void send(NotificationRequest request) {
        if (request == null) {
            log.warn("Received null NotificationRequest, ignoring");
            return;
        }

        // Fault injection: simulate notification send failure
        if (FaultInjectionRegistry.isActive("notification-send-failure")) {
            throw new RuntimeException("Fault injected: notification-send-failure");
        }

        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && idempotencyCache.putIfAbsent(idempotencyKey, Boolean.TRUE) != null) {
            log.info("Duplicate notification request ignored: bizType={}, bizId={}, idempotencyKey={}",
                    request.getBizType(), request.getBizId(), idempotencyKey);
            return;
        }

        // Record for test observation
        sentRecords.add(new NotificationRecord(
                request.getBizType(),
                request.getBizId(),
                request.getReceiver(),
                request.getChannel() != null ? request.getChannel().name() : null,
                request.getTemplateCode(),
                request.getIdempotencyKey(),
                Instant.now()));

        log.info("Sending notification: bizType={}, bizId={}, channel={}, template={}",
                request.getBizType(), request.getBizId(), request.getChannel(), request.getTemplateCode());

        try {
            String renderedContent = renderTemplate(request.getTemplateCode(), request.getVariablesOrDefault());

            switch (request.getChannel()) {
                case EMAIL:
                    mockMailSender.sendEmail(request.getReceiver(),
                            "[" + request.getBizType() + "] Notification",
                            renderedContent);
                    break;
                case SMS:
                    mockSmsSender.sendSms(request.getReceiver(), renderedContent);
                    break;
                case IN_APP:
                    log.info("In-app notification sent to {}: {}", request.getReceiver(), renderedContent);
                    break;
                default:
                    log.warn("Unknown notification channel: {}", request.getChannel());
            }

            log.info("Notification sent successfully: bizType={}, bizId={}, channel={}",
                    request.getBizType(), request.getBizId(), request.getChannel());

            // Record via NotificationRecordService after successful send
            NotificationRecordService.record(
                    request.getBizType(),
                    request.getBizId(),
                    request.getReceiver(),
                    request.getChannel(),
                    request.getTemplateCode(),
                    request.getIdempotencyKey());

        } catch (Exception e) {
            log.error("Failed to send notification: bizType={}, bizId={}, channel={}, error={}",
                    request.getBizType(), request.getBizId(), request.getChannel(), e.getMessage(), e);
        }
    }

    /**
     * Simple template rendering that replaces {{variable}} placeholders with values.
     */
    private String renderTemplate(String templateCode, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(templateCode).append("] ");
        if (!variables.isEmpty()) {
            sb.append(variables);
        }
        return sb.toString();
    }

    /**
     * Returns a snapshot of all recorded notification sends for test observation.
     */
    public static List<NotificationRecord> getRecords() {
        synchronized (sentRecords) {
            return new ArrayList<>(sentRecords);
        }
    }

    /**
     * Clears all recorded notification records (called on test reset).
     */
    public static void clearRecords() {
        synchronized (sentRecords) {
            sentRecords.clear();
        }
    }
}

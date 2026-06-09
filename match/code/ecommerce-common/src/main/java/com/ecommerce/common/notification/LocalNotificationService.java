package com.ecommerce.common.notification;

/**
 * Central notification service interface.
 * All business modules MUST send notifications through this service.
 * Direct use of MockMailSender or MockSmsSender is forbidden by architecture rules.
 */
public interface LocalNotificationService {

    /**
     * Sends a notification based on the provided request.
     * Implementations must handle idempotency, template rendering,
     * channel routing, and failure logging.
     *
     * @param request the notification request
     */
    void send(NotificationRequest request);
}

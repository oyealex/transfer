package com.ecommerce.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock email sender that logs instead of actually sending emails.
 * <p>This is an internal implementation detail of LocalNotificationServiceImpl.
 * Business modules MUST NOT inject or use this class directly.
 */
class MockMailSender {

    private static final Logger log = LoggerFactory.getLogger(MockMailSender.class);

    /**
     * Logs the email instead of actually sending it.
     *
     * @param to      the recipient email address
     * @param subject the email subject
     * @param body    the email body
     */
    public void sendEmail(String to, String subject, String body) {
        log.info("MOCK EMAIL: to={}, subject={}, body={}", to, subject, body);
    }
}

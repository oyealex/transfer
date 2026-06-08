package com.ecommerce.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mock SMS sender that logs instead of actually sending SMS messages.
 */
@Component
public class MockSmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    /**
     * Logs the SMS instead of actually sending it.
     *
     * @param phone   the recipient phone number
     * @param content the SMS content
     */
    public void sendSms(String phone, String content) {
        log.info("MOCK SMS: phone={}, content={}", phone, content);
    }
}

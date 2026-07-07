package com.ecommerce.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates unique order numbers in the format SO + yyyyMMdd + 4-digit sequence.
 *
 * <p>Order number format: SO202606070001
 * <ul>
 *   <li>Prefix: SO (Shop Order)</li>
 *   <li>Date part: yyyyMMdd (e.g., 20260607)</li>
 *   <li>Sequence: 4-digit incrementing number, resetting daily</li>
 * </ul>
 *
 * <p>In a production system, this would use a distributed sequence service
 * (e.g., database sequence, Redis atomic counter, or Snowflake-style ID).
 * For this implementation, we use an in-memory AtomicInteger with date tracking.
 */
@Component
public class OrderNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrderNumberGenerator.class);

    private static final String PREFIX = "SO";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_SEQUENCE = 9999;

    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String currentDate;

    public OrderNumberGenerator() {
        this.currentDate = LocalDate.now().format(DATE_FORMAT);
        log.info("OrderNumberGenerator initialized with date={}", currentDate);
    }

    /**
     * Generate the next unique order number.
     *
     * @return the next order number (e.g., "SO202606070001")
     */
    public synchronized String nextOrderNo() {
        String today = LocalDate.now().format(DATE_FORMAT);

        // Reset sequence if date changed
        if (!today.equals(currentDate)) {
            log.info("Date changed from {} to {}, resetting order number sequence", currentDate, today);
            currentDate = today;
            sequence.set(0);
        }

        int seq = sequence.incrementAndGet();
        if (seq > MAX_SEQUENCE) {
            // Wrap around — in production this would use a larger sequence
            log.warn("Order sequence exceeded MAX_SEQUENCE ({}), wrapping to 0. "
                    + "Consider using a larger sequence size in production.", MAX_SEQUENCE);
            sequence.set(0);
            seq = sequence.incrementAndGet();
        }

        String orderNo = PREFIX + today + String.format("%04d", seq);
        log.debug("Generated order number: {}", orderNo);
        return orderNo;
    }

    /**
     * Get the current sequence count for today (for monitoring).
     */
    public int getCurrentSequence() {
        return sequence.get();
    }

    /**
     * Get the current date portion being used.
     */
    public String getCurrentDate() {
        return currentDate;
    }

    /**
     * Validate that an order number conforms to the expected format.
     */
    public static boolean isValidOrderNo(String orderNo) {
        if (orderNo == null || orderNo.length() != 16) {
            return false;
        }
        if (!orderNo.startsWith(PREFIX)) {
            return false;
        }
        String datePart = orderNo.substring(2, 10);
        try {
            LocalDate.parse(datePart, DATE_FORMAT);
        } catch (Exception e) {
            return false;
        }
        String seqPart = orderNo.substring(10);
        try {
            int seq = Integer.parseInt(seqPart);
            return seq >= 1 && seq <= MAX_SEQUENCE + 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

package com.ecommerce.order.util;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Utility methods for order validation.
 * Provides static validation helpers used across multiple services.
 */
public final class OrderValidationUtils {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("^SO\\d{8}\\d{4}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_ITEMS_PER_ORDER = 100;
    private static final int MAX_QUANTITY_PER_ITEM = 999;
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("50000.00");

    private OrderValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validate order number format: SO + yyyyMMdd + 4-digit sequence.
     */
    public static void validateOrderNo(String orderNo) {
        if (orderNo == null || !ORDER_NO_PATTERN.matcher(orderNo).matches()) {
            throw new BusinessException("INVALID_ORDER_NO",
                    "Invalid order number format: " + orderNo
                            + ". Expected format: SOyyyyMMddNNNN");
        }
    }

    /**
     * Validate phone number format (Chinese mobile number).
     */
    public static void validatePhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("INVALID_PHONE",
                    "Invalid phone number format: " + phone);
        }
    }

    /**
     * Validate items per order limit.
     */
    public static void validateItemCountLimit(int count) {
        if (count <= 0) {
            throw new BusinessException("ORDER_EMPTY", "Order must have at least one item");
        }
        if (count > MAX_ITEMS_PER_ORDER) {
            throw new BusinessException("ORDER_TOO_MANY_ITEMS",
                    "Order cannot have more than " + MAX_ITEMS_PER_ORDER
                            + " items, got: " + count);
        }
    }

    /**
     * Validate quantity per item.
     */
    public static void validateQuantityPerItem(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_QUANTITY",
                    "Item quantity must be positive, got: " + quantity);
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BusinessException("QUANTITY_TOO_LARGE",
                    "Item quantity cannot exceed " + MAX_QUANTITY_PER_ITEM
                            + ", got: " + quantity);
        }
    }

    /**
     * Validate that an order amount is within acceptable bounds.
     */
    public static void validateOrderAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ORDER_INVALID_AMOUNT",
                    "Order amount must be positive, got: " + amount);
        }
        if (amount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BusinessException("ORDER_AMOUNT_TOO_LARGE",
                    "Order amount cannot exceed " + MAX_ORDER_AMOUNT
                            + ", got: " + amount);
        }
    }

    /**
     * Validate address fields are not empty.
     */
    public static void validateAddress(String province, String city, String district, String detail) {
        if (isBlank(province)) {
            throw new BusinessException("ADDRESS_MISSING", "Province is required");
        }
        if (isBlank(city)) {
            throw new BusinessException("ADDRESS_MISSING", "City is required");
        }
        if (isBlank(district)) {
            throw new BusinessException("ADDRESS_MISSING", "District is required");
        }
        if (isBlank(detail)) {
            throw new BusinessException("ADDRESS_MISSING", "Detail address is required");
        }
    }

    /**
     * Validate receiver information.
     */
    public static void validateReceiver(String name, String phone) {
        if (isBlank(name)) {
            throw new BusinessException("RECEIVER_MISSING", "Receiver name is required");
        }
        if (isBlank(phone)) {
            throw new BusinessException("RECEIVER_MISSING", "Receiver phone is required");
        }
        validatePhone(phone);
    }

    /**
     * Validate a date range for statistics queries.
     */
    public static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("DATE_REQUIRED",
                    "Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("DATE_INVALID",
                    "Start date " + startDate + " is after end date " + endDate);
        }
        long daysBetween = startDate.until(endDate).getDays();
        if (daysBetween > 90) {
            throw new BusinessException("DATE_RANGE_TOO_LARGE",
                    "Date range cannot exceed 90 days, got " + daysBetween + " days");
        }
    }

    /**
     * Validate that a status is not terminal before modification.
     */
    public static void assertNotTerminal(OrderStatus status) {
        if (status == OrderStatus.CANCELLED
                || status == OrderStatus.CLOSED
                || status == OrderStatus.COMPLETED
                || status == OrderStatus.REFUNDED) {
            throw new BusinessException("ORDER_TERMINAL",
                    "Cannot modify order in terminal status: " + status);
        }
    }

    /**
     * Validate that an order belongs to the given user.
     */
    public static void assertOwnership(Long orderUserId, Long requestUserId) {
        if (!orderUserId.equals(requestUserId)) {
            throw new BusinessException("ORDER_NOT_OWNED",
                    "Order does not belong to user " + requestUserId);
        }
    }

    /**
     * Parse a date string in yyyyMMdd format.
     */
    public static LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("DATE_PARSE_ERROR",
                    "Invalid date format: " + dateStr + ". Expected: yyyyMMdd");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

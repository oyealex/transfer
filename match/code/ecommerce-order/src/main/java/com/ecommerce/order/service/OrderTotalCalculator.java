package com.ecommerce.order.service;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculates the total amounts for an order including item totals,
 * shipping fee, packaging fee, and the final payable amount.
 */
@Component
public class OrderTotalCalculator {

    private static final Logger log = LoggerFactory.getLogger(OrderTotalCalculator.class);

    private static final BigDecimal SHIPPING_FEE = new BigDecimal("8.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");
    private static final BigDecimal PACKAGING_FEE_PER_ITEM = new BigDecimal("1.00");
    private static final BigDecimal MIN_PAYABLE_AMOUNT = new BigDecimal("0.01");

    /**
     * Calculate the item total from a list of order items.
     * Sum of (price * quantity) for each item.
     */
    public BigDecimal calculateItemTotal(List<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            BigDecimal lineTotal = MonetaryUtil.multiply(item.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            total = MonetaryUtil.add(total, lineTotal);
            item.setSubtotal(lineTotal);
        }
        log.debug("Calculated item total: {}", total);
        return total;
    }

    /**
     * Calculate the shipping fee.
     * Free if item total >= 199.00, otherwise 8.00.
     */
    public BigDecimal calculateShippingFee(BigDecimal itemTotal) {
        if (itemTotal == null || itemTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (itemTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return SHIPPING_FEE;
    }

    /**
     * Calculate the packaging fee.
     * 1.00 per item in the order.
     */
    public BigDecimal calculatePackagingFee(int itemCount) {
        if (itemCount <= 0) {
            return BigDecimal.ZERO;
        }
        return MonetaryUtil.multiply(PACKAGING_FEE_PER_ITEM, BigDecimal.valueOf(itemCount));
    }

    /**
     * Calculate the final payable amount for the order.
     *
     * @param itemTotal             sum of all item subtotals
     * @param shippingFee           shipping fee
     * @param packagingFee          packaging fee
     * @param discountAmount        total discount from promotions
     * @param pointsDeductionAmount amount deducted via points
     * @return the final payable amount
     */
    public BigDecimal calculate(BigDecimal itemTotal, BigDecimal shippingFee,
                                BigDecimal packagingFee, BigDecimal discountAmount,
                                BigDecimal pointsDeductionAmount) {
        BigDecimal payableAmount = MonetaryUtil.add(itemTotal, packagingFee);
        payableAmount = MonetaryUtil.subtract(payableAmount, discountAmount);
        payableAmount = MonetaryUtil.subtract(payableAmount, pointsDeductionAmount);

        // Ensure payable amount is at least the minimum
        if (payableAmount.compareTo(MIN_PAYABLE_AMOUNT) < 0) {
            payableAmount = MIN_PAYABLE_AMOUNT;
        }

        log.info("Calculated payable: itemTotal={}, shippingFee={}, packagingFee={}, "
                        + "discount={}, pointsDeduction={}, finalPayable={}",
                itemTotal, shippingFee, packagingFee, discountAmount,
                pointsDeductionAmount, payableAmount);

        return payableAmount;
    }

    /**
     * Apply the calculated amounts and the saved Order entity.
     */
    public void applyToOrder(Order order, BigDecimal itemTotal, BigDecimal shippingFee,
                             BigDecimal packagingFee, BigDecimal discountAmount,
                             BigDecimal pointsDeductionAmount, BigDecimal payableAmount) {
        order.setItemTotal(itemTotal);
        order.setShippingFee(shippingFee);
        order.setPackagingFee(packagingFee);
        order.setDiscountAmount(discountAmount);
        order.setPointsDeductionAmount(pointsDeductionAmount);
        order.setPayableAmount(payableAmount);
    }
}

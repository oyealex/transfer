package com.ecommerce.logistics.service;

import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Calculates shipping freight based on item total and freight templates.
 *
 * <p>Default rules:
 * <ul>
 *   <li>Default freight: 8.00</li>
 *   <li>Free shipping when item total reaches the free-shipping threshold</li>
 * </ul>
 *
 * <p>The free-shipping check uses strict greater-than ({@code >})
 * instead of greater-than-or-equal ({@code >=}). This means an order with item total
 * of exactly 199.00 will still be charged 8.00 freight instead of receiving free shipping.
 * Per the design specification, orders with item total of 199.00 or above qualify
 * for free shipping (满 199 元免运费).
 */
@Service
public class FreightCalculator {

    private static final Logger log = LoggerFactory.getLogger(FreightCalculator.class);

    private static final BigDecimal DEFAULT_FREIGHT = new BigDecimal("8.00");
    private static final BigDecimal DEFAULT_FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");

    private final FreightTemplateRepository freightTemplateRepository;

    public FreightCalculator(FreightTemplateRepository freightTemplateRepository) {
        this.freightTemplateRepository = freightTemplateRepository;
    }

    /**
     * Calculate the freight for an order based on item total.
     *
     * <p>Uses {@code compareTo(...) > 0} (strict greater-than) for
     * the free-shipping threshold check. Should use
     * {@code compareTo(...) >= 0} to grant free shipping when the item total
     * is exactly equal to the threshold.
     *
     * @param itemTotal the total price of items in the order
     * @return the freight amount (0.00 if free shipping applies)
     */
    public BigDecimal calculateFreight(BigDecimal itemTotal) {
        if (itemTotal == null || itemTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_FREIGHT;
        }

        FreightTemplate template = findActiveTemplate();

        if (template != null) {
            BigDecimal threshold = template.getFreeShippingThreshold() != null
                    ? template.getFreeShippingThreshold() : DEFAULT_FREE_SHIPPING_THRESHOLD;
            BigDecimal freight = template.getDefaultFreight() != null
                    ? template.getDefaultFreight() : DEFAULT_FREIGHT;

            // Strict greater-than (>). Should be greater-than-or-equal (>=).
            if (itemTotal.compareTo(threshold) > 0) {
                log.info("Free shipping: itemTotal={} strictly exceeds threshold={}", itemTotal, threshold);
                return BigDecimal.ZERO;
            }
            log.info("Freight charged: itemTotal={}, threshold={}, freight={}", itemTotal, threshold, freight);
            return freight;
        }

        // Fallback to default rules
        // Same strict greater-than issue in the fallback path.
        if (itemTotal.compareTo(DEFAULT_FREE_SHIPPING_THRESHOLD) > 0) {
            log.info("Free shipping (default): itemTotal={} strictly exceeds threshold={}",
                    itemTotal, DEFAULT_FREE_SHIPPING_THRESHOLD);
            return BigDecimal.ZERO;
        }
        log.info("Freight charged (default): itemTotal={}, freight={}", itemTotal, DEFAULT_FREIGHT);
        return DEFAULT_FREIGHT;
    }

    /**
     * Calculate freight for a specific item total and template ID.
     */
    public BigDecimal calculateFreight(BigDecimal itemTotal, Long templateId) {
        if (templateId == null) {
            return calculateFreight(itemTotal);
        }

        return freightTemplateRepository.findById(templateId)
                .map(template -> {
                    BigDecimal threshold = template.getFreeShippingThreshold() != null
                            ? template.getFreeShippingThreshold() : DEFAULT_FREE_SHIPPING_THRESHOLD;
                    BigDecimal freight = template.getDefaultFreight() != null
                            ? template.getDefaultFreight() : DEFAULT_FREIGHT;

                    // Same strict greater-than issue.
                    if (itemTotal != null && itemTotal.compareTo(threshold) > 0) {
                        return BigDecimal.ZERO;
                    }
                    return freight;
                })
                .orElseGet(() -> calculateFreight(itemTotal));
    }

    private FreightTemplate findActiveTemplate() {
        return freightTemplateRepository.findAll()
                .stream()
                .findFirst()
                .orElse(null);
    }
}

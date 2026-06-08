package com.ecommerce.logistics.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Freight template defining shipping cost rules.
 *
 * <p>Templates support province-based pricing, weight-based pricing,
 * and free-shipping thresholds. The default configuration is:
 * <ul>
 *   <li>Default freight: 8.00</li>
 *   <li>Free shipping threshold: 199.00</li>
 * </ul>
 */
@Entity
@Table(name = "freight_templates", indexes = {
        @Index(name = "idx_freight_templates_name", columnList = "name")
})
public class FreightTemplate extends BaseEntity {

    /** Template name */
    @Column(nullable = false, length = 128)
    private String name;

    /** Default freight cost when no special rules apply */
    @Column(name = "default_freight", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultFreight;

    /** Order item total threshold for free shipping */
    @Column(name = "free_shipping_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal freeShippingThreshold;

    /**
     * JSON array of province-specific rules.
     * Format: [{"province":"Guangdong","freight":5.00},...]
     */
    @Column(name = "province_rules", columnDefinition = "TEXT")
    private String provinceRules;

    /**
     * JSON array of weight-based rules.
     * Format: [{"maxWeightKg":1.0,"freight":8.00},{"maxWeightKg":5.0,"freight":15.00},...]
     */
    @Column(name = "weight_rules", columnDefinition = "TEXT")
    private String weightRules;

    public FreightTemplate() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDefaultFreight() {
        return defaultFreight;
    }

    public void setDefaultFreight(BigDecimal defaultFreight) {
        this.defaultFreight = defaultFreight;
    }

    public BigDecimal getFreeShippingThreshold() {
        return freeShippingThreshold;
    }

    public void setFreeShippingThreshold(BigDecimal freeShippingThreshold) {
        this.freeShippingThreshold = freeShippingThreshold;
    }

    public String getProvinceRules() {
        return provinceRules;
    }

    public void setProvinceRules(String provinceRules) {
        this.provinceRules = provinceRules;
    }

    public String getWeightRules() {
        return weightRules;
    }

    public void setWeightRules(String weightRules) {
        this.weightRules = weightRules;
    }
}

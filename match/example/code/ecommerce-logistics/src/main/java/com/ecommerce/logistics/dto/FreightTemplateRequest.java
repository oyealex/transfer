package com.ecommerce.logistics.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Request DTO for creating or updating a freight template.
 */
public class FreightTemplateRequest {

    @NotBlank
    private String name;

    @DecimalMin("0.00")
    private BigDecimal defaultFreight;

    @DecimalMin("0.00")
    private BigDecimal freeShippingThreshold;

    /** JSON: province-specific freight rules */
    private String provinceRules;

    /** JSON: weight-based freight rules */
    private String weightRules;

    public FreightTemplateRequest() {
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

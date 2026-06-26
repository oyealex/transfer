package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.logistics.dto.FreightTemplateRequest;
import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for managing freight templates.
 *
 * <p>Freight templates define shipping cost rules including default freight,
 * free-shipping thresholds, province-specific pricing, and weight-based pricing.
 */
@Service
@Transactional
public class FreightTemplateService {

    private static final Logger log = LoggerFactory.getLogger(FreightTemplateService.class);

    private static final BigDecimal DEFAULT_FREIGHT = new BigDecimal("8.00");
    private static final BigDecimal DEFAULT_FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");

    private final FreightTemplateRepository freightTemplateRepository;

    public FreightTemplateService(FreightTemplateRepository freightTemplateRepository) {
        this.freightTemplateRepository = freightTemplateRepository;
    }

    /**
     * Create a new freight template.
     *
     * @param request the template creation request
     * @return the created template
     */
    public FreightTemplate createTemplate(FreightTemplateRequest request) {
        FreightTemplate template = new FreightTemplate();
        template.setName(request.getName());
        template.setDefaultFreight(request.getDefaultFreight() != null
                ? request.getDefaultFreight() : DEFAULT_FREIGHT);
        template.setFreeShippingThreshold(request.getFreeShippingThreshold() != null
                ? request.getFreeShippingThreshold() : DEFAULT_FREE_SHIPPING_THRESHOLD);
        template.setProvinceRules(request.getProvinceRules());
        template.setWeightRules(request.getWeightRules());

        template = freightTemplateRepository.save(template);

        log.info("Freight template created: id={}, name={}, defaultFreight={}, threshold={}",
                template.getId(), template.getName(),
                template.getDefaultFreight(), template.getFreeShippingThreshold());

        return template;
    }

    /**
     * Update an existing freight template.
     *
     * @param templateId the template ID
     * @param request    the update request
     * @return the updated template
     */
    public FreightTemplate updateTemplate(Long templateId, FreightTemplateRequest request) {
        FreightTemplate template = freightTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Freight template not found: " + templateId));

        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getDefaultFreight() != null) {
            template.setDefaultFreight(request.getDefaultFreight());
        }
        if (request.getFreeShippingThreshold() != null) {
            template.setFreeShippingThreshold(request.getFreeShippingThreshold());
        }
        if (request.getProvinceRules() != null) {
            template.setProvinceRules(request.getProvinceRules());
        }
        if (request.getWeightRules() != null) {
            template.setWeightRules(request.getWeightRules());
        }

        template = freightTemplateRepository.save(template);

        log.info("Freight template updated: id={}", templateId);

        return template;
    }

    /**
     * Get all freight templates.
     */
    @Transactional(readOnly = true)
    public List<FreightTemplate> getAllTemplates() {
        return freightTemplateRepository.findAll();
    }

    /**
     * Get a freight template by ID.
     */
    @Transactional(readOnly = true)
    public FreightTemplate getTemplate(Long templateId) {
        return freightTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Freight template not found: " + templateId));
    }

    /**
     * Delete a freight template.
     */
    public void deleteTemplate(Long templateId) {
        if (!freightTemplateRepository.existsById(templateId)) {
            throw new ResourceNotFoundException("Freight template not found: " + templateId);
        }
        freightTemplateRepository.deleteById(templateId);
        log.info("Freight template deleted: id={}", templateId);
    }
}

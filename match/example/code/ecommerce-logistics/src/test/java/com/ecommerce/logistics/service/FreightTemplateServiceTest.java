package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.logistics.dto.FreightTemplateRequest;
import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FreightTemplateService}.
 */
@ExtendWith(MockitoExtension.class)
class FreightTemplateServiceTest {

    @Mock
    private FreightTemplateRepository freightTemplateRepository;

    @InjectMocks
    private FreightTemplateService freightTemplateService;

    // ==================== createTemplate ====================

    @Test
    void testCreateTemplate_withAllFields() {
        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("Standard Shipping");
        request.setDefaultFreight(new BigDecimal("10.00"));
        request.setFreeShippingThreshold(new BigDecimal("299.00"));
        request.setProvinceRules("[{\"province\":\"Guangdong\",\"freight\":5.00}]");
        request.setWeightRules("[{\"maxWeightKg\":1.0,\"freight\":10.00}]");

        when(freightTemplateRepository.save(any(FreightTemplate.class))).thenAnswer(inv -> {
            FreightTemplate t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        FreightTemplate result = freightTemplateService.createTemplate(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Standard Shipping", result.getName());
        assertEquals(new BigDecimal("10.00"), result.getDefaultFreight());
        assertEquals(new BigDecimal("299.00"), result.getFreeShippingThreshold());
        assertEquals("[{\"province\":\"Guangdong\",\"freight\":5.00}]", result.getProvinceRules());
        assertEquals("[{\"maxWeightKg\":1.0,\"freight\":10.00}]", result.getWeightRules());
    }

    @Test
    void testCreateTemplate_withDefaults_whenNullFields() {
        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("Default Template");
        // defaultFreight and freeShippingThreshold are null

        when(freightTemplateRepository.save(any(FreightTemplate.class))).thenAnswer(inv -> {
            FreightTemplate t = inv.getArgument(0);
            t.setId(2L);
            return t;
        });

        FreightTemplate result = freightTemplateService.createTemplate(request);

        assertEquals("Default Template", result.getName());
        assertEquals(new BigDecimal("8.00"), result.getDefaultFreight());
        assertEquals(new BigDecimal("199.00"), result.getFreeShippingThreshold());
    }

    // ==================== updateTemplate ====================

    @Test
    void testUpdateTemplate_updatesAllFields() {
        FreightTemplate existing = new FreightTemplate();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setDefaultFreight(new BigDecimal("5.00"));
        existing.setFreeShippingThreshold(new BigDecimal("100.00"));

        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(freightTemplateRepository.save(any(FreightTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("Updated Name");
        request.setDefaultFreight(new BigDecimal("12.00"));
        request.setFreeShippingThreshold(new BigDecimal("250.00"));
        request.setProvinceRules("[{\"province\":\"Beijing\"}]");
        request.setWeightRules("[{\"maxWeightKg\":3.0,\"freight\":20.00}]");

        FreightTemplate result = freightTemplateService.updateTemplate(1L, request);

        assertEquals("Updated Name", result.getName());
        assertEquals(new BigDecimal("12.00"), result.getDefaultFreight());
        assertEquals(new BigDecimal("250.00"), result.getFreeShippingThreshold());
        assertEquals("[{\"province\":\"Beijing\"}]", result.getProvinceRules());
        assertEquals("[{\"maxWeightKg\":3.0,\"freight\":20.00}]", result.getWeightRules());
    }

    @Test
    void testUpdateTemplate_partialUpdate_ignoresNullFields() {
        FreightTemplate existing = new FreightTemplate();
        existing.setId(1L);
        existing.setName("Keep Name");
        existing.setDefaultFreight(new BigDecimal("5.00"));
        existing.setFreeShippingThreshold(new BigDecimal("100.00"));

        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(freightTemplateRepository.save(any(FreightTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName(null); // should be ignored
        request.setDefaultFreight(new BigDecimal("15.00"));
        // other fields null — should not overwrite

        FreightTemplate result = freightTemplateService.updateTemplate(1L, request);

        assertEquals("Keep Name", result.getName());
        assertEquals(new BigDecimal("15.00"), result.getDefaultFreight());
        assertEquals(new BigDecimal("100.00"), result.getFreeShippingThreshold()); // unchanged
    }

    @Test
    void testUpdateTemplate_notFound_throwsException() {
        when(freightTemplateRepository.findById(999L)).thenReturn(Optional.empty());

        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("New Name");

        assertThrows(ResourceNotFoundException.class,
                () -> freightTemplateService.updateTemplate(999L, request));
    }

    // ==================== getAllTemplates ====================

    @Test
    void testGetAllTemplates_returnsList() {
        FreightTemplate t1 = new FreightTemplate();
        t1.setId(1L);
        t1.setName("Template 1");
        FreightTemplate t2 = new FreightTemplate();
        t2.setId(2L);
        t2.setName("Template 2");

        when(freightTemplateRepository.findAll()).thenReturn(Arrays.asList(t1, t2));

        List<FreightTemplate> result = freightTemplateService.getAllTemplates();

        assertEquals(2, result.size());
        assertEquals("Template 1", result.get(0).getName());
        assertEquals("Template 2", result.get(1).getName());
    }

    @Test
    void testGetAllTemplates_emptyList() {
        when(freightTemplateRepository.findAll()).thenReturn(Collections.emptyList());

        List<FreightTemplate> result = freightTemplateService.getAllTemplates();

        assertTrue(result.isEmpty());
    }

    // ==================== getTemplate ====================

    @Test
    void testGetTemplate_success() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Test Template");
        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        FreightTemplate result = freightTemplateService.getTemplate(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Template", result.getName());
    }

    @Test
    void testGetTemplate_notFound_throwsException() {
        when(freightTemplateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> freightTemplateService.getTemplate(999L));
    }

    // ==================== deleteTemplate ====================

    @Test
    void testDeleteTemplate_success() {
        when(freightTemplateRepository.existsById(1L)).thenReturn(true);

        freightTemplateService.deleteTemplate(1L);

        verify(freightTemplateRepository).deleteById(1L);
    }

    @Test
    void testDeleteTemplate_notFound_throwsException() {
        when(freightTemplateRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> freightTemplateService.deleteTemplate(999L));

        verify(freightTemplateRepository, never()).deleteById(999L);
    }
}

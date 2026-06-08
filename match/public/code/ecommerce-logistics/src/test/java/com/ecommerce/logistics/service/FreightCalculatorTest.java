package com.ecommerce.logistics.service;

import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FreightCalculator}.
 *
 * <p>Verifies the actual behavior of the freight calculation logic.
 * The free-shipping check uses strict greater-than (&gt;) instead
 * of greater-than-or-equal (&gt;=). An order with itemTotal of exactly 199.00
 * will still be charged 8.00 freight.
 */
@ExtendWith(MockitoExtension.class)
class FreightCalculatorTest {

    @Mock
    private FreightTemplateRepository freightTemplateRepository;

    private FreightCalculator calculator;

    @BeforeEach
    void setUp() {
        lenient().when(freightTemplateRepository.findAll()).thenReturn(Collections.emptyList());
        calculator = new FreightCalculator(freightTemplateRepository);
    }

    /**
     * itemTotal=199.00 does NOT qualify for free shipping
     * because the check uses {@code compareTo(threshold) > 0} (strict greater-than).
     * Per the design specification, 199.00 should qualify for free shipping.
     */
    @Test
    void testCalculate_exactly199_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("199.00"));
        assertEquals(new BigDecimal("8.00"), result,
                "itemTotal=199.00 should be free shipping, but strict > charges freight");
    }

    @Test
    void testCalculate_over199_freeShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("200.00"));
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testCalculate_below199_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_zeroAmount_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(BigDecimal.ZERO);
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_nullAmount_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(null);
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_negativeAmount_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("-10.00"));
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_justAboveThreshold_freeShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("199.01"));
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testCalculate_withActiveTemplate_overridesThreshold() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Express");
        template.setDefaultFreight(new BigDecimal("15.00"));
        template.setFreeShippingThreshold(new BigDecimal("299.00"));

        when(freightTemplateRepository.findAll()).thenReturn(Collections.singletonList(template));

        // 250.00 < 299.00 threshold, so freight is charged
        BigDecimal result = calculator.calculateFreight(new BigDecimal("250.00"));
        assertEquals(new BigDecimal("15.00"), result);

        // 300.00 > 299.00 threshold, free shipping
        BigDecimal result2 = calculator.calculateFreight(new BigDecimal("300.00"));
        assertEquals(BigDecimal.ZERO, result2);
    }

    @Test
    void testCalculate_withTemplateId_matchesTemplate() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Special");
        template.setDefaultFreight(new BigDecimal("20.00"));
        template.setFreeShippingThreshold(new BigDecimal("500.00"));

        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        // 400.00 < 500.00 threshold via template, charges freight
        BigDecimal result = calculator.calculateFreight(new BigDecimal("400.00"), 1L);
        assertEquals(new BigDecimal("20.00"), result);

        // 600.00 > 500.00 threshold, free shipping
        BigDecimal result2 = calculator.calculateFreight(new BigDecimal("600.00"), 1L);
        assertEquals(BigDecimal.ZERO, result2);
    }

    @Test
    void testCalculate_withTemplateId_nullFallsBackToDefault() {
        when(freightTemplateRepository.findAll()).thenReturn(Collections.emptyList());

        BigDecimal result = calculator.calculateFreight(new BigDecimal("100.00"), null);
        assertEquals(new BigDecimal("8.00"), result);
    }
}

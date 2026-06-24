package com.ecommerce.logistics.controller;

import com.ecommerce.logistics.dto.FreightTemplateRequest;
import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.service.FreightTemplateService;
import com.ecommerce.logistics.service.LogisticsCallbackService;
import com.ecommerce.logistics.service.ShipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminLogisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShipmentService shipmentService;

    @MockBean
    private FreightTemplateService freightTemplateService;

    @MockBean
    private LogisticsCallbackService callbackService;

    private static void authenticateAsAdmin() {
        TestApplication.TestSecurityContextRepository.setTestAuth(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ==================== POST /api/v1/admin/logistics/shipments/{id}/pick ====================

    @Test
    void testPick_authenticated_returnsOk() throws Exception {
        authenticateAsAdmin();
        doNothing().when(shipmentService).pick(eq(1L), eq(null));

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/pick"))
                .andExpect(status().isOk());

        verify(shipmentService).pick(1L, null);
    }

    @Test
    void testPick_unauthenticated_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/pick"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPick_wrongRole_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.setTestAuth(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/pick"))
                .andExpect(status().isForbidden());
    }

    // ==================== POST /api/v1/admin/logistics/shipments/{id}/print-label ====================

    @Test
    void testPrintLabel_authenticated_returnsOk() throws Exception {
        authenticateAsAdmin();
        doNothing().when(shipmentService).printLabel(eq(1L), eq("DEFAULT"));

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/print-label"))
                .andExpect(status().isOk());

        verify(shipmentService).printLabel(1L, "DEFAULT");
    }

    @Test
    void testPrintLabel_unauthenticated_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/print-label"))
                .andExpect(status().isForbidden());
    }

    // ==================== POST /api/v1/admin/logistics/shipments/{id}/outbound ====================

    @Test
    void testOutbound_authenticated_returnsOk() throws Exception {
        authenticateAsAdmin();
        doNothing().when(shipmentService).outbound(1L);

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/outbound"))
                .andExpect(status().isOk());

        verify(shipmentService).outbound(1L);
    }

    @Test
    void testOutbound_unauthenticated_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        mockMvc.perform(post("/api/v1/admin/logistics/shipments/1/outbound"))
                .andExpect(status().isForbidden());
    }

    // ==================== POST /api/v1/admin/logistics/freight-templates ====================

    @Test
    void testCreateFreightTemplate_authenticated_returnsCreated() throws Exception {
        authenticateAsAdmin();

        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("Express Shipping");
        request.setDefaultFreight(new BigDecimal("15.00"));
        request.setFreeShippingThreshold(new BigDecimal("299.00"));
        request.setProvinceRules("[{\"province\":\"Guangdong\",\"freight\":5.00}]");
        request.setWeightRules("[{\"maxWeightKg\":2.0,\"freight\":15.00}]");

        FreightTemplate created = new FreightTemplate();
        created.setId(1L);
        created.setName("Express Shipping");
        created.setDefaultFreight(new BigDecimal("15.00"));
        created.setFreeShippingThreshold(new BigDecimal("299.00"));
        created.setProvinceRules("[{\"province\":\"Guangdong\",\"freight\":5.00}]");
        created.setWeightRules("[{\"maxWeightKg\":2.0,\"freight\":15.00}]");

        when(freightTemplateService.createTemplate(any(FreightTemplateRequest.class)))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/logistics/freight-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Express Shipping"))
                .andExpect(jsonPath("$.defaultFreight").value(15.00))
                .andExpect(jsonPath("$.freeShippingThreshold").value(299.00))
                .andExpect(jsonPath("$.provinceRules").value("[{\"province\":\"Guangdong\",\"freight\":5.00}]"))
                .andExpect(jsonPath("$.weightRules").value("[{\"maxWeightKg\":2.0,\"freight\":15.00}]"));

        verify(freightTemplateService).createTemplate(any(FreightTemplateRequest.class));
    }

    @Test
    void testCreateFreightTemplate_validationFails_missingName() throws Exception {
        authenticateAsAdmin();

        FreightTemplateRequest request = new FreightTemplateRequest();
        // name is blank → @NotBlank validation should fail

        mockMvc.perform(post("/api/v1/admin/logistics/freight-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateFreightTemplate_unauthenticated_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("Test");

        mockMvc.perform(post("/api/v1/admin/logistics/freight-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateFreightTemplate_wrongRole_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.setTestAuth(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        FreightTemplateRequest request = new FreightTemplateRequest();
        request.setName("Test");

        mockMvc.perform(post("/api/v1/admin/logistics/freight-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @AfterEach
    void tearDown() {
        TestApplication.TestSecurityContextRepository.clearTestAuth();
        SecurityContextHolder.clearContext();
    }
}

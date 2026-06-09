package com.ecommerce.logistics.controller;

import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.dto.ShipmentResponse;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link LogisticsController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShipmentService shipmentService;

    @MockBean
    private LogisticsCallbackService callbackService;

    @MockBean
    private FreightTemplateService freightTemplateService;

    // ==================== GET /api/v1/logistics/order/{orderId} ====================

    @Test
    void testGetLogisticsByOrderId_authenticated_returnsShipment() throws Exception {
        TestApplication.TestSecurityContextRepository.setTestAuth(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        ShipmentResponse response = new ShipmentResponse();
        response.setId(1L);
        response.setShipmentNo("SH202406010001");
        response.setOrderId(100L);
        response.setUserId(200L);
        response.setStatus("OUTBOUND");
        response.setFreightAmount(new BigDecimal("8.00"));
        response.setCreatedAt(LocalDateTime.of(2024, 6, 1, 10, 0));

        when(shipmentService.getShipmentByOrderId(100L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/logistics/order/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.shipmentNo").value("SH202406010001"))
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.status").value("OUTBOUND"))
                .andExpect(jsonPath("$.freightAmount").value(8.00))
                .andExpect(jsonPath("$.userId").value(200));
    }

    @Test
    void testGetLogisticsByOrderId_unauthenticated_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        mockMvc.perform(get("/api/v1/logistics/order/100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetLogisticsByOrderId_wrongRole_returnsForbidden() throws Exception {
        TestApplication.TestSecurityContextRepository.setTestAuth(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/v1/logistics/order/100"))
                .andExpect(status().isForbidden());
    }

    // ==================== POST /api/v1/logistics/callback ====================

    @Test
    void testReceiveCallback_noAuthRequired_returnsOk() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        LogisticsCallbackRequest request = new LogisticsCallbackRequest();
        request.setTrackingNo("TN12345");
        request.setStatus("DELIVERED");
        request.setLocation("Shanghai Hub");
        request.setDescription("Delivered");
        request.setEventTime(LocalDateTime.of(2024, 6, 1, 12, 0));
        request.setSignature("SIG001");

        doNothing().when(callbackService).processCallback(any(LogisticsCallbackRequest.class));

        mockMvc.perform(post("/api/v1/logistics/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(callbackService).processCallback(any(LogisticsCallbackRequest.class));
    }

    @Test
    void testReceiveCallback_emptyBody_returnsOk() throws Exception {
        TestApplication.TestSecurityContextRepository.clearTestAuth();

        doNothing().when(callbackService).processCallback(any(LogisticsCallbackRequest.class));

        mockMvc.perform(post("/api/v1/logistics/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        TestApplication.TestSecurityContextRepository.clearTestAuth();
        SecurityContextHolder.clearContext();
    }
}

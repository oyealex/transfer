package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.config.SecurityConfig;
import com.ecommerce.user.service.AddressService;
import com.ecommerce.user.service.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@Import({JwtTokenProvider.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789abcdef0123456789abcdef",
        "security.jwt.issuer=test-issuer",
        "security.jwt.expire-minutes=120"
})
@DisplayName("AddressController")
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AddressService addressService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String authHeader() {
        return "Bearer " + jwtTokenProvider.generateToken(1L, List.of("USER"));
    }

    private AddressRequest addressRequest() {
        AddressRequest request = new AddressRequest();
        request.setProvince("浙江");
        request.setCity("杭州");
        request.setDistrict("西湖区");
        request.setDetail("文三路478号");
        request.setReceiverName("张三");
        request.setReceiverPhone("13900139000");
        request.setDefault(false);
        return request;
    }

    private AddressResponse addressResponse() {
        AddressResponse response = new AddressResponse();
        response.setAddressId(10L);
        response.setProvince("浙江");
        response.setCity("杭州");
        response.setDistrict("西湖区");
        response.setDetail("文三路478号");
        response.setReceiverName("张三");
        response.setReceiverPhone("13900139000");
        response.setDefault(false);
        return response;
    }

    // --- POST /api/v1/users/addresses ---

    @Test
    @DisplayName("returns 201 Created when creating an address with valid authentication")
    void testCreateAddress_authenticated_returns201() throws Exception {
        AddressResponse response = addressResponse();
        when(addressService.createAddress(eq(1L), any(AddressRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/addresses")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addressId").value(10))
                .andExpect(jsonPath("$.province").value("浙江"))
                .andExpect(jsonPath("$.receiverName").value("张三"));
    }

    @Test
    @DisplayName("returns 403 Forbidden when creating address without authentication")
    void testCreateAddress_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest())))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/v1/users/addresses ---

    @Test
    @DisplayName("returns 200 OK with address list for authenticated user")
    void testListAddresses_authenticated_returns200() throws Exception {
        AddressResponse response = addressResponse();
        when(addressService.listAddresses(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/users/addresses")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].addressId").value(10))
                .andExpect(jsonPath("$[0].city").value("杭州"));
    }

    @Test
    @DisplayName("returns empty list when user has no addresses")
    void testListAddresses_empty_returnsEmptyList() throws Exception {
        when(addressService.listAddresses(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/addresses")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- PUT /api/v1/users/addresses/{addressId} ---

    @Test
    @DisplayName("returns 200 OK when updating an address")
    void testUpdateAddress_authenticated_returns200() throws Exception {
        AddressResponse response = addressResponse();
        response.setDetail("新地址详情");
        when(addressService.updateAddress(eq(1L), eq(10L), any(AddressRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/users/addresses/10")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").value("新地址详情"));
    }

    @Test
    @DisplayName("returns 403 Forbidden when updating address without authentication")
    void testUpdateAddress_unauthenticated_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/users/addresses/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest())))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/v1/users/addresses/{addressId} ---

    @Test
    @DisplayName("returns 204 No Content when deleting an address")
    void testDeleteAddress_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/addresses/10")
                        .header("Authorization", authHeader()))
                .andExpect(status().isNoContent());

        verify(addressService).deleteAddress(1L, 10L);
    }

    @Test
    @DisplayName("returns 403 Forbidden when deleting address without authentication")
    void testDeleteAddress_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/addresses/10"))
                .andExpect(status().isForbidden());
    }
}

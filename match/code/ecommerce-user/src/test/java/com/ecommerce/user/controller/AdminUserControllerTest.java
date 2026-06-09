package com.ecommerce.user.controller;

import com.ecommerce.user.config.SecurityConfig;
import com.ecommerce.user.service.JwtTokenProvider;
import com.ecommerce.user.service.UserAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({JwtTokenProvider.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789abcdef0123456789abcdef",
        "security.jwt.issuer=test-issuer",
        "security.jwt.expire-minutes=120"
})
@DisplayName("AdminUserController")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserAuthService userAuthService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String adminAuthHeader() {
        return "Bearer " + jwtTokenProvider.generateToken(1L, List.of("ADMIN"));
    }

    private String userAuthHeader() {
        return "Bearer " + jwtTokenProvider.generateToken(2L, List.of("USER"));
    }

    // --- POST /api/v1/admin/users/{userId}/freeze ---

    @Test
    @DisplayName("returns 200 OK when ADMIN freezes a user")
    void testFreezeUser_adminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/freeze")
                        .header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk());

        verify(userAuthService).freezeUser(5L);
    }

    @Test
    @DisplayName("returns 403 Forbidden when non-ADMIN user tries to freeze")
    void testFreezeUser_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/freeze")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("returns 403 Forbidden when unauthenticated request tries to freeze")
    void testFreezeUser_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/freeze"))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/v1/admin/users/{userId}/unfreeze ---

    @Test
    @DisplayName("returns 200 OK when ADMIN unfreezes a user")
    void testUnfreezeUser_adminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/unfreeze")
                        .header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk());

        verify(userAuthService).unfreezeUser(5L);
    }

    @Test
    @DisplayName("returns 403 Forbidden when non-ADMIN user tries to unfreeze")
    void testUnfreezeUser_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/unfreeze")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("returns 403 Forbidden when unauthenticated request tries to unfreeze")
    void testUnfreezeUser_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/5/unfreeze"))
                .andExpect(status().isForbidden());
    }
}

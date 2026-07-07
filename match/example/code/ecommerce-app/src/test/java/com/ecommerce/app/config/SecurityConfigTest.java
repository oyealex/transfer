package com.ecommerce.app.config;

import com.ecommerce.app.CorsConfig;
import com.ecommerce.app.SecurityConfig;
import com.ecommerce.user.service.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityConfigTest.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "security-config-test"})
@TestPropertySource(properties = {
        "security.jwt.secret=this-is-a-very-long-secret-key-for-testing-purposes-only",
        "security.jwt.issuer=test-issuer",
        "security.jwt.expire-minutes=60"
})
@DisplayName("SecurityConfig")
class SecurityConfigTest {

    /**
     * Minimal configuration: SecurityConfig, JwtTokenProvider,
     * test controllers, web MVC, and security auto-configuration.
     */
    @org.springframework.boot.SpringBootConfiguration
    @ImportAutoConfiguration({
            WebMvcAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class,
            ErrorMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            CorsConfig.class,
            JwtTokenProvider.class,
            PublicTestController.class,
            UserTestController.class,
            AdminTestController.class
    })
    static class TestConfig {
    }

    @RestController
    @Profile("security-config-test")
    static class PublicTestController {
        @GetMapping("/api/v1/products")
        String products() { return "ok"; }

        @PostMapping("/api/v1/users/register")
        String register() { return "ok"; }

        @PostMapping("/api/v1/users/login")
        String login() { return "ok"; }
    }

    @RestController
    @Profile("security-config-test")
    static class UserTestController {
        @GetMapping("/api/v1/cart")
        String cart() { return "ok"; }

        @GetMapping("/api/v1/orders")
        String orders() { return "ok"; }
    }

    @RestController
    @Profile("security-config-test")
    static class AdminTestController {
        @GetMapping("/api/v1/admin/test")
        String admin() { return "ok"; }

        @PostMapping("/api/v1/admin/test")
        String adminPost() { return "ok"; }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    // --- Public endpoints ---

    @Test
    @DisplayName("GET /api/v1/products is accessible without authentication")
    void testPublicEndpoints_accessibleWithoutAuth_products() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("POST /api/v1/users/register is accessible without authentication")
    void testPublicEndpoints_accessibleWithoutAuth_register() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/users/login is accessible without authentication")
    void testPublicEndpoints_accessibleWithoutAuth_login() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // --- User-role-protected endpoints ---

    @Test
    @DisplayName("GET /api/v1/cart returns 403 without authentication")
    void testUserEndpoints_requireAuth_cart_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/cart is accessible with USER role")
    void testUserEndpoints_requireAuth_cart_withUserRole() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/orders returns 403 without authentication")
    void testUserEndpoints_requireAuth_orders_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/orders is accessible with USER role")
    void testUserEndpoints_requireAuth_orders_withUserRole() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isOk());
    }

    // --- Admin-role-protected endpoints ---

    @Test
    @DisplayName("/api/v1/admin/test returns 403 without authentication")
    void testAdminEndpoints_requireAdminRole_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/api/v1/admin/test returns 403 with USER role")
    void testAdminEndpoints_requireAdminRole_withUserRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/test returns 403 without authentication")
    void testAdminEndpoints_requireAdminRole_post_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/admin/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/test returns 403 with USER role")
    void testAdminEndpoints_requireAdminRole_post_withUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden());
    }

    // --- PasswordEncoder ---

    @Test
    @DisplayName("passwordEncoder bean is an instance of BCryptPasswordEncoder")
    void testPasswordEncoder_isBCrypt() {
        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder,
                "Expected passwordEncoder to be a BCryptPasswordEncoder");
    }
}

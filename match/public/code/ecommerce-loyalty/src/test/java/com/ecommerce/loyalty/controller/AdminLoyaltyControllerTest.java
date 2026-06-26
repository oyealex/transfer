package com.ecommerce.loyalty.controller;

import com.ecommerce.loyalty.service.PointsExpireService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminLoyaltyController.class)
class AdminLoyaltyControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http.securityContext(securityContext -> securityContext
                    .securityContextRepository(new TestSecurityContextRepository()));
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            http.csrf(csrf -> csrf.disable());
            return http.build();
        }
    }

    /**
     * Custom SecurityContextRepository that avoids calling
     * SecurityContextHolder.getContext() inside containsContext/loadContext
     * to prevent recursive deferred-context resolution (StackOverflow)
     * in Spring Security 6.x.
     */
    static class TestSecurityContextRepository implements SecurityContextRepository {

        static Authentication testAuthentication;

        @Override
        public SecurityContext loadContext(HttpRequestResponseHolder holder) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            if (testAuthentication != null) {
                context.setAuthentication(testAuthentication);
            }
            return context;
        }

        @Override
        public void saveContext(SecurityContext context, HttpServletRequest request,
                                HttpServletResponse response) {
            // No-op for tests
        }

        @Override
        public boolean containsContext(HttpServletRequest request) {
            return testAuthentication != null;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointsExpireService pointsExpireService;

    @BeforeEach
    void setUp() {
        // Store auth via static holder to avoid StackOverflow in SecurityContextRepository
        TestSecurityContextRepository.testAuthentication =
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                TestSecurityContextRepository.testAuthentication);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContextRepository.testAuthentication = null;
        SecurityContextHolder.clearContext();
    }

    /**
     * POST /api/v1/admin/loyalty/points/expire — ADMIN endpoint
     *
     * <p>The controller returns 200 OK with success=true but
     * the underlying PointsExpireService.expire() is a no-op. No points
     * are actually expired.
     */
    @Test
    void testExpirePoints_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/admin/loyalty/points/expire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Points expiration processed"));

        // Verify the expire service was called (it is a no-op)
        verify(pointsExpireService).expire();
    }
}

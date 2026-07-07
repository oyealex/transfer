package com.ecommerce.loyalty.controller;

import com.ecommerce.loyalty.dto.PointsEstimateRequest;
import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import com.ecommerce.loyalty.service.MemberLevelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoyaltyController.class)
class LoyaltyControllerTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoyaltyPointService loyaltyPointService;

    @MockBean
    private MemberLevelService memberLevelService;

    @MockBean
    private PointsTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        // Store auth via static holder to avoid StackOverflow in SecurityContextRepository
        TestSecurityContextRepository.testAuthentication =
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                TestSecurityContextRepository.testAuthentication);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContextRepository.testAuthentication = null;
        SecurityContextHolder.clearContext();
    }

    // ---- GET /api/v1/loyalty/points ----

    @Test
    void testGetPoints_returnsPointsResponse() throws Exception {
        LoyaltyAccount account = createAccount(1L, MemberLevel.GOLD, 2500, 2000, 100);

        when(loyaltyPointService.getAccountByUserId(1L)).thenReturn(account);

        mockMvc.perform(get("/api/v1/loyalty/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.totalPoints").value(2500))
                .andExpect(jsonPath("$.availablePoints").value(2000))
                .andExpect(jsonPath("$.frozenPoints").value(100))
                .andExpect(jsonPath("$.memberLevel").value("GOLD"))
                .andExpect(jsonPath("$.memberLevelName").value("Gold Member"));
    }

    // ---- POST /api/v1/loyalty/points/estimate-redeem ----

    @Test
    void testEstimateRedeem_returnsCorrectResponse() throws Exception {
        LoyaltyAccount account = createAccount(1L, MemberLevel.NORMAL, 5000, 5000, 0);
        when(loyaltyPointService.getAccountByUserId(1L)).thenReturn(account);
        when(loyaltyPointService.estimateRedeemPoints(any(BigDecimal.class), eq(1L)))
                .thenReturn(5000);

        PointsEstimateRequest request = new PointsEstimateRequest();
        request.setOrderAmount(new BigDecimal("100"));
        request.setRedeemPoints(2000);

        mockMvc.perform(post("/api/v1/loyalty/points/estimate-redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxRedeemablePoints").value(5000))
                .andExpect(jsonPath("$.actualRedeemPoints").value(2000))
                .andExpect(jsonPath("$.redeemAmount").value(20.0))
                .andExpect(jsonPath("$.remainingPoints").value(3000));
    }

    // ---- GET /api/v1/loyalty/points/history ----

    @Test
    void testGetHistory_returnsPagedHistory() throws Exception {
        PointsTransaction tx = new PointsTransaction();
        tx.setId(10L);
        tx.setUserId(1L);
        tx.setType(PointsTransactionType.EARN);
        tx.setAmount(500);
        tx.setBalance(500);
        tx.setDescription("Test earn");
        tx.setCreatedAt(LocalDateTime.of(2025, 6, 1, 12, 0));

        PageImpl<PointsTransaction> page = new PageImpl<>(
                List.of(tx), PageRequest.of(0, 20), 1);

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/loyalty/points/history")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].type").value("EARN"))
                .andExpect(jsonPath("$.items[0].amount").value(500))
                .andExpect(jsonPath("$.items[0].balance").value(500))
                .andExpect(jsonPath("$.items[0].description").value("Test earn"));
    }

    // ---- GET /api/v1/loyalty/member-level ----

    @Test
    void testGetMemberLevel_returnsLevelResponse() throws Exception {
        LoyaltyAccount account = createAccount(1L, MemberLevel.SILVER, 3000, 3000, 0);
        account.setAnnualConsumption(new BigDecimal("3500"));

        when(memberLevelService.evaluateAndUpgrade(1L)).thenReturn(MemberLevel.SILVER);
        when(loyaltyPointService.getAccountByUserId(1L)).thenReturn(account);

        mockMvc.perform(get("/api/v1/loyalty/member-level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("SILVER"))
                .andExpect(jsonPath("$.levelName").value("Silver Member"))
                .andExpect(jsonPath("$.multiplier").value(1.1))
                .andExpect(jsonPath("$.annualConsumption").value(3500.0))
                .andExpect(jsonPath("$.nextLevelCondition")
                        .value("Annual consumption >= 5,000 to reach Gold"));
    }

    // ---- helper ----

    private LoyaltyAccount createAccount(Long userId, MemberLevel level,
                                          int totalPoints, int availablePoints, int frozenPoints) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setMemberLevel(level);
        account.setTotalPoints(totalPoints);
        account.setAvailablePoints(availablePoints);
        account.setFrozenPoints(frozenPoints);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        return account;
    }
}

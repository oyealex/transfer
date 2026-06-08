package com.ecommerce.review.controller;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.review.dto.ReviewApprovalRequest;
import com.ecommerce.review.service.ReviewModerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link AdminReviewController} using standalone MockMvc setup.
 *
 * <p>The {@code approve} endpoint publishes a
 * {@link com.ecommerce.review.event.ReviewApprovedEvent}, awarding 20 points.
 * Because {@code ReviewService.createReview} also publishes this event on
 * submission, the user receives 40 review reward points instead of 20.
 */
@DisplayName("AdminReviewController")
class AdminReviewControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ReviewModerationService reviewModerationService;
    private ReviewApprovalRequest approveRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        reviewModerationService = mock(ReviewModerationService.class);

        AdminReviewController controller = new AdminReviewController(reviewModerationService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jacksonConverter)
                .build();

        setupMockAuthentication("1", "ROLE_ADMIN");

        approveRequest = new ReviewApprovalRequest();
        approveRequest.setApproved(true);
        approveRequest.setReviewerNote("Looks appropriate");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupMockAuthentication(String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        when(auth.getAuthorities()).thenAnswer(inv -> authorities);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/admin/reviews/{reviewId}/approve
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/reviews/{reviewId}/approve")
    class ApproveReview {

        @Test
        @DisplayName("approveReview: returns 200 OK for ADMIN")
        void testApproveReview_returns200() throws Exception {
            setupMockAuthentication("1", "ROLE_ADMIN");
            doNothing().when(reviewModerationService)
                    .approve(eq(10L), eq(1L), anyString());

            mockMvc.perform(post("/api/v1/admin/reviews/10/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(approveRequest)))
                    .andExpect(status().isOk());
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/admin/reviews/{reviewId}/reject
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/reviews/{reviewId}/reject")
    class RejectReview {

        @Test
        @DisplayName("rejectReview: returns 200 OK for ADMIN")
        void testRejectReview_returns200() throws Exception {
            setupMockAuthentication("1", "ROLE_ADMIN");
            approveRequest.setApproved(false);
            approveRequest.setReviewerNote("Inappropriate content");

            doNothing().when(reviewModerationService)
                    .reject(eq(10L), eq(1L), anyString());

            mockMvc.perform(post("/api/v1/admin/reviews/10/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(approveRequest)))
                    .andExpect(status().isOk());
        }
    }
}

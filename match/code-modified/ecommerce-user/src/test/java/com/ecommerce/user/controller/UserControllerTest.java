package com.ecommerce.user.controller;

import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.config.SecurityConfig;
import com.ecommerce.user.service.JwtTokenProvider;
import com.ecommerce.user.service.UserAuthService;
import com.ecommerce.user.service.UserRegisterService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({JwtTokenProvider.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789abcdef0123456789abcdef",
        "security.jwt.issuer=test-issuer",
        "security.jwt.expire-minutes=120"
})
@DisplayName("UserController")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRegisterService userRegisterService;

    @MockBean
    private UserAuthService userAuthService;

    @MockBean
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- POST /api/v1/users/register ---

    @Test
    @DisplayName("returns 201 Created on successful user registration")
    void testRegister_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPhone("13800138000");
        request.setPassword("Password123");
        request.setNickname("NewUser");

        UserResponse response = new UserResponse();
        response.setUserId(1L);
        response.setEmail("new@example.com");
        response.setNickname("NewUser");
        response.setStatus(UserStatus.ACTIVE);
        response.setRole(UserRole.USER);

        when(userRegisterService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // --- POST /api/v1/users/login ---

    @Test
    @DisplayName("returns 200 OK with JWT token on successful login")
    void testLogin_returns200_withToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("correctPassword");

        LoginResponse response = new LoginResponse("jwt-token-string", 1L, List.of("USER"));

        when(userAuthService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-string"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    // --- GET /api/v1/users/me ---

    @Test
    @DisplayName("returns 200 OK for authenticated user requesting their own info")
    void testGetMe_authenticated_returns200() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPhone("13800138000");
        user.setNickname("TestUser");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String token = jwtTokenProvider.generateToken(1L, List.of("USER"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.nickname").value("TestUser"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("returns 403 Forbidden when requesting user info without authentication")
    void testGetMe_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }
}

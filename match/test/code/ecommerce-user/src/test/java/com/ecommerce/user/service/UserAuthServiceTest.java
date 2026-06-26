package com.ecommerce.user.service;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.dto.ActivateRequest;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.EmailActivationToken;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.EmailActivationTokenRepository;
import com.ecommerce.user.repository.LoginSessionRepository;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthService")
class UserAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailActivationTokenRepository activationTokenRepository;

    @Mock
    private LoginSessionRepository loginSessionRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserAuthService userAuthService;

    // --- Helpers ---

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("active@example.com");
        user.setPhone("13800138000");
        user.setPasswordHash("$2a$10$hashed");
        user.setNickname("ActiveUser");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return user;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    // --- Login tests ---

    @Test
    @DisplayName("returns JWT token on successful login with valid credentials")
    void testLogin_validCredentials_returnsJwtToken() {
        User user = activeUser();
        LoginRequest request = loginRequest("active@example.com", "correctPassword");

        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, List.of("USER"))).thenReturn("jwt-token-value");

        LoginResponse response = userAuthService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token-value");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getRoles()).containsExactly("USER");
        verify(loginSessionRepository).save(any());
        verify(jwtTokenProvider).generateToken(1L, List.of("USER"));
    }

    @Test
    @DisplayName("rejects login when user account is FROZEN")
    void testLogin_userNotActive_throwsException() {
        User frozenUser = activeUser();
        frozenUser.setStatus(UserStatus.FROZEN);
        LoginRequest request = loginRequest("active@example.com", "correctPassword");

        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(frozenUser));

        assertThatThrownBy(() -> userAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account is frozen");
    }

    @Test
    @DisplayName("rejects login when user account status is not ACTIVE (non-FROZEN)")
    void testLogin_pendingActivationStatus_throwsException() {
        User pendingUser = activeUser();
        pendingUser.setStatus(UserStatus.PENDING_ACTIVATION);
        LoginRequest request = loginRequest("active@example.com", "correctPassword");

        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> userAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account is not active");
    }

    @Test
    @DisplayName("throws AuthorizationException when password does not match")
    void testLogin_wrongPassword_throwsException() {
        User user = activeUser();
        LoginRequest request = loginRequest("active@example.com", "wrongPassword");

        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> userAuthService.login(request))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Invalid password");
        verify(jwtTokenProvider, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when user email is not found")
    void testLogin_userNotFound_throwsException() {
        LoginRequest request = loginRequest("unknown@example.com", "anyPassword");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAuthService.login(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // --- Activate tests ---

    @Test
    @DisplayName("activates user account with a valid token and returns UserResponse")
    void testActivate_withToken_returnsUserResponse() {
        ActivateRequest activateRequest = new ActivateRequest();
        activateRequest.setToken("valid-activation-token");

        User user = activeUser();
        user.setStatus(UserStatus.PENDING_ACTIVATION);

        EmailActivationToken activationToken = new EmailActivationToken();
        activationToken.setUserId(1L);
        activationToken.setToken("valid-activation-token");
        activationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        activationToken.setUsed(false);

        when(activationTokenRepository.findByToken("valid-activation-token"))
                .thenReturn(Optional.of(activationToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userAuthService.activate(activateRequest);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(activationTokenRepository).save(activationToken);
        assertThat(activationToken.isUsed()).isTrue();
    }

    @Test
    @DisplayName("throws exception when activation token is already used")
    void testActivate_alreadyUsedToken_throwsException() {
        ActivateRequest activateRequest = new ActivateRequest();
        activateRequest.setToken("used-token");

        EmailActivationToken usedToken = new EmailActivationToken();
        usedToken.setUserId(1L);
        usedToken.setToken("used-token");
        usedToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        usedToken.setUsed(true);

        when(activationTokenRepository.findByToken("used-token"))
                .thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> userAuthService.activate(activateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    @DisplayName("throws exception when activation token is expired")
    void testActivate_expiredToken_throwsException() {
        ActivateRequest activateRequest = new ActivateRequest();
        activateRequest.setToken("expired-token");

        EmailActivationToken expiredToken = new EmailActivationToken();
        expiredToken.setUserId(1L);
        expiredToken.setToken("expired-token");
        expiredToken.setExpiresAt(LocalDateTime.now().minusHours(1));
        expiredToken.setUsed(false);

        when(activationTokenRepository.findByToken("expired-token"))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> userAuthService.activate(activateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    // --- Freeze / unfreeze tests ---

    @Test
    @DisplayName("changes user status to FROZEN when freezeUser is called")
    void testFreezeUser_changesStatusToFrozen() {
        User user = activeUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userAuthService.freezeUser(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.FROZEN);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changes user status to ACTIVE when unfreezeUser is called")
    void testUnfreezeUser_changesStatusToActive() {
        User user = activeUser();
        user.setStatus(UserStatus.FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userAuthService.unfreezeUser(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when freezing a non-existent user")
    void testFreezeUser_userNotFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAuthService.freezeUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when unfreezing a non-existent user")
    void testUnfreezeUser_userNotFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAuthService.unfreezeUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

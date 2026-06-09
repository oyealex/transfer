package com.ecommerce.user.service;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.dto.ActivateRequest;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.EmailActivationToken;
import com.ecommerce.user.entity.LoginSession;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.EmailActivationTokenRepository;
import com.ecommerce.user.repository.LoginSessionRepository;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Handles authentication: login, email activation, freeze/unfreeze.
 */
@Service
public class UserAuthService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthService.class);

    private final UserRepository userRepository;
    private final EmailActivationTokenRepository activationTokenRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserAuthService(UserRepository userRepository,
                           EmailActivationTokenRepository activationTokenRepository,
                           LoginSessionRepository loginSessionRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Authenticates a user by email and password.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getEmail()));

        if (user.getStatus() != UserStatus.ACTIVE) {
            if (user.getStatus() == UserStatus.FROZEN) {
                throw new BusinessException("USER_FROZEN", "Account is frozen: " + user.getEmail());
            }
            throw new BusinessException("USER_NOT_ACTIVE", "Account is not active: " + user.getEmail());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthorizationException("UNAUTHORIZED", "Invalid password");
        }

        List<String> roles = Collections.singletonList(user.getRole().name());
        String token = jwtTokenProvider.generateToken(user.getId(), roles);

        // Record login session
        LoginSession session = new LoginSession();
        session.setUserId(user.getId());
        session.setToken(token);
        session.setLoginTime(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(120));
        loginSessionRepository.save(session);

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return new LoginResponse(token, user.getId(), roles);
    }

    /**
     * Activates a user account using an email activation token.
     */
    @Transactional
    public UserResponse activate(ActivateRequest request) {
        EmailActivationToken activationToken = activationTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired activation token"));

        if (activationToken.isUsed()) {
            throw new BusinessException("CONFLICT", "Activation token has already been used");
        }

        if (activationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("CONFLICT", "Activation token has expired");
        }

        User user = userRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + activationToken.getUserId()));

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        log.info("User activated: id={}, email={}", user.getId(), user.getEmail());
        return UserResponse.from(user);
    }

    /**
     * Freezes a user account, preventing login and ordering.
     */
    @Transactional
    public void freezeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setStatus(UserStatus.FROZEN);
        userRepository.save(user);
        log.info("User frozen: id={}", userId);
    }

    /**
     * Unfreezes (reactivates) a previously frozen user account.
     */
    @Transactional
    public void unfreezeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("User unfrozen: id={}", userId);
    }
}

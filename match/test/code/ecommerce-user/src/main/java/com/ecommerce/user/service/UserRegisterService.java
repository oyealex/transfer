package com.ecommerce.user.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.EmailActivationToken;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.EmailActivationTokenRepository;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles user registration.
 */
@Service
public class UserRegisterService {

    private static final Logger log = LoggerFactory.getLogger(UserRegisterService.class);

    private final UserRepository userRepository;
    private final EmailActivationTokenRepository activationTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LocalNotificationService notificationService;

    public UserRegisterService(UserRepository userRepository,
                               EmailActivationTokenRepository activationTokenRepository,
                               BCryptPasswordEncoder passwordEncoder,
                               LocalNotificationService notificationService) {
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Check uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone already registered: " + request.getPhone());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}, status={}", saved.getId(), saved.getEmail(), saved.getStatus());

        // Generate email activation token
        EmailActivationToken activationToken = new EmailActivationToken();
        activationToken.setUserId(saved.getId());
        activationToken.setToken(UUID.randomUUID().toString());
        activationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        activationToken.setUsed(false);
        activationTokenRepository.save(activationToken);
        log.info("Activation token generated for user id={}", saved.getId());

        // Send activation email via LocalNotificationService
        NotificationRequest notification = new NotificationRequest();
        notification.setBizType("USER_REGISTER");
        notification.setBizId(String.valueOf(saved.getId()));
        notification.setReceiver(saved.getEmail());
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setTemplateCode("ACTIVATION");
        Map<String, Object> variables = new HashMap<>();
        variables.put("nickname", saved.getNickname());
        variables.put("activationToken", activationToken.getToken());
        notification.setVariables(variables);
        notificationService.send(notification);

        UserResponse response = UserResponse.from(saved);
        response.setActivationToken(activationToken.getToken());
        return response;
    }
}

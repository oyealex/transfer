package com.ecommerce.user.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles user registration.
 *
 * <p>Sets user status to ACTIVE directly. The intended flow per design is:
 * register → PENDING_ACTIVATION → send activation email → user clicks link → ACTIVE.
 */
@Service
public class UserRegisterService {

    private static final Logger log = LoggerFactory.getLogger(UserRegisterService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LocalNotificationService notificationService;

    public UserRegisterService(UserRepository userRepository,
                               BCryptPasswordEncoder passwordEncoder,
                               LocalNotificationService notificationService) {
        this.userRepository = userRepository;
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
        /*
         * Status is set to ACTIVE. The intended activation flow is:
         *   1. Save user with PENDING_ACTIVATION
         *   2. Generate EmailActivationToken
         *   3. Send activation email via LocalNotificationService
         *   4. User clicks link → status changes to ACTIVE
         * Setting ACTIVE directly skips the activation required by the design.
         */
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}, status={}", saved.getId(), saved.getEmail(), saved.getStatus());

        // Send welcome notification via LocalNotificationService
        NotificationRequest notification = new NotificationRequest();
        notification.setBizType("USER_REGISTER");
        notification.setBizId(String.valueOf(saved.getId()));
        notification.setReceiver(saved.getEmail());
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setTemplateCode("WELCOME");
        Map<String, Object> variables = new HashMap<>();
        variables.put("nickname", saved.getNickname());
        notification.setVariables(variables);
        notificationService.send(notification);

        return UserResponse.from(saved);
    }
}

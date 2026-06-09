package com.ecommerce.user.controller;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.dto.ActivateRequest;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.UserAuthService;
import com.ecommerce.user.service.UserRegisterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public user endpoints: register, activate, login, and current-user info.
 */
@RestController
public class UserController {

    private final UserRegisterService userRegisterService;
    private final UserAuthService userAuthService;
    private final UserRepository userRepository;

    public UserController(UserRegisterService userRegisterService,
                          UserAuthService userAuthService,
                          UserRepository userRepository) {
        this.userRegisterService = userRegisterService;
        this.userAuthService = userAuthService;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/v1/users/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userRegisterService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/v1/users/activate")
    public ResponseEntity<UserResponse> activate(@Valid @RequestBody ActivateRequest request) {
        UserResponse response = userAuthService.activate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/users/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userAuthService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/users/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}

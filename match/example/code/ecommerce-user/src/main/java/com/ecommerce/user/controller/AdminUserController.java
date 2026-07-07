package com.ecommerce.user.controller;

import com.ecommerce.user.service.UserAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for managing user accounts (freeze / unfreeze).
 * Requires ADMIN role.
 */
@RestController
public class AdminUserController {

    private final UserAuthService userAuthService;

    public AdminUserController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/api/v1/admin/users/{userId}/freeze")
    public ResponseEntity<Void> freezeUser(@PathVariable Long userId) {
        userAuthService.freezeUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/admin/users/{userId}/unfreeze")
    public ResponseEntity<Void> unfreezeUser(@PathVariable Long userId) {
        userAuthService.unfreezeUser(userId);
        return ResponseEntity.ok().build();
    }
}

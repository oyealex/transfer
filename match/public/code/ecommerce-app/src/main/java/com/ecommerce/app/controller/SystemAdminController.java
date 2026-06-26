package com.ecommerce.app.controller;

import com.ecommerce.common.notification.NotificationRecordService;
import com.ecommerce.common.test.FaultInjectionRegistry;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/system")
public class SystemAdminController {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminController.class);

    private static final String ADMIN_EMAIL = "admin@shophub.test";
    private static final String ADMIN_PASSWORD = "Admin123!";
    private static final String ADMIN_PHONE = "13800000001";
    private static final String ADMIN_NICKNAME = "Admin";

    private final ApplicationContext applicationContext;
    private final CacheManager cacheManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public SystemAdminController(ApplicationContext applicationContext,
                                  CacheManager cacheManager,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtTokenProvider jwtTokenProvider) {
        this.applicationContext = applicationContext;
        this.cacheManager = cacheManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/reset-sandbox")
    public ResponseEntity<Map<String, Boolean>> resetSandbox() {
        log.info("Admin reset-sandbox triggered: clearing all JPA repositories and caches");

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<String, JpaRepository> repoBeans = applicationContext.getBeansOfType(JpaRepository.class);
        List<Map.Entry<String, JpaRepository>> remaining = new ArrayList<>(repoBeans.entrySet());
        remaining.sort(Comparator.comparingInt(entry -> deletePriority(entry.getKey())));

        for (int pass = 0; pass < 3 && !remaining.isEmpty(); pass++) {
            List<Map.Entry<String, JpaRepository>> failed = new ArrayList<>();
            for (Map.Entry<String, JpaRepository> entry : remaining) {
                try {
                    entry.getValue().deleteAll();
                } catch (Exception e) {
                    log.debug("deleteAll() failed for repository {}, will retry: {}",
                            entry.getKey(), e.getMessage());
                    failed.add(entry);
                }
            }
            remaining = failed;
        }

        if (!remaining.isEmpty()) {
            log.warn("{} repositories failed to clear after 3 retry passes", remaining.size());
        }

        for (String cacheName : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        RuntimeConfigRegistry.clear();
        SystemClockService.reset();
        FaultInjectionRegistry.clear();
        NotificationRecordService.clear();

        return ResponseEntity.ok(Map.of("reset", true));
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<Map<String, Object>> bootstrapAdmin() {
        log.info("Admin bootstrap-admin triggered");

        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElse(null);

        if (admin == null) {
            admin = new User();
            admin.setEmail(ADMIN_EMAIL);
            admin.setPhone(ADMIN_PHONE);
            admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setNickname(ADMIN_NICKNAME);
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            admin = userRepository.save(admin);
            log.info("Created admin user with id: {}", admin.getId());
        } else {
            log.info("Admin user already exists with id: {}", admin.getId());
        }

        String token = jwtTokenProvider.generateToken(admin.getId(),
                Collections.singletonList(UserRole.ADMIN.name()));

        Map<String, Object> result = Map.of(
                "userId", admin.getId(),
                "email", admin.getEmail(),
                "token", token
        );

        return ResponseEntity.ok(result);
    }

    @PutMapping("/configs/{key}")
    public ResponseEntity<Map<String, Object>> putConfig(@PathVariable String key,
                                                          @RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
        }
        RuntimeConfigRegistry.put(key, value);
        log.info("Config set: {} = {}", key, value);
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @GetMapping("/configs/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String key) {
        Object value = RuntimeConfigRegistry.getOrDefault(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/clock")
    public ResponseEntity<Map<String, Object>> setClock(@RequestBody Map<String, Object> body) {
        if (body.containsKey("offsetMinutes")) {
            long offset = ((Number) body.get("offsetMinutes")).longValue();
            SystemClockService.setOffset(offset);
            log.info("Clock offset set to {} minutes", offset);
            return ResponseEntity.ok(Map.of("offsetMinutes", offset));
        } else if (body.containsKey("timestamp")) {
            String timestamp = (String) body.get("timestamp");
            try {
                LocalDateTime fixed = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                SystemClockService.setFixed(fixed);
                log.info("Clock fixed at {}", fixed);
                return ResponseEntity.ok(Map.of("timestamp", fixed.toString()));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid timestamp format, use ISO_LOCAL_DATE_TIME"));
            }
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Either offsetMinutes or timestamp is required"));
    }

    @DeleteMapping("/clock")
    public ResponseEntity<Map<String, Object>> resetClock() {
        SystemClockService.reset();
        log.info("Clock reset to system time");
        return ResponseEntity.ok(Map.of("reset", true));
    }

    private int deletePriority(String repositoryBeanName) {
        if ("cartItemRepository".equals(repositoryBeanName)) {
            return 0;
        }
        if ("cartRepository".equals(repositoryBeanName)) {
            return 10;
        }
        return 5;
    }
}

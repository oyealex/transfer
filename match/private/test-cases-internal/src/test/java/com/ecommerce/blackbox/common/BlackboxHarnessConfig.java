package com.ecommerce.blackbox.common;

import com.ecommerce.common.notification.NotificationRecordService;
import com.ecommerce.common.test.FaultInjectionRegistry;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.logistics.query.OrderLogisticsStatusUpdater;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserRole;
import com.ecommerce.user.entity.UserStatus;
import com.ecommerce.user.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestConfiguration
public class BlackboxHarnessConfig {

    static final String ADMIN_EMAIL = "admin@shophub.test";
    static final String ADMIN_PASSWORD = "Admin123!";
    private static final String ADMIN_PHONE = "13800000001";

    @Bean
    ApplicationRunner blackboxHarnessInitializer(UserRepository userRepository,
                                                PasswordEncoder passwordEncoder) {
        return args -> {
            RuntimeConfigRegistry.clear();
            FaultInjectionRegistry.clear();
            SystemClockService.reset();
            NotificationRecordService.clear();

            if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
                User admin = new User();
                admin.setEmail(ADMIN_EMAIL);
                admin.setPhone(ADMIN_PHONE);
                admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
                admin.setNickname("Admin");
                admin.setRole(UserRole.ADMIN);
                admin.setStatus(UserStatus.ACTIVE);
                userRepository.save(admin);
            }
        };
    }

    @Bean
    OrderLogisticsStatusUpdater orderLogisticsStatusUpdater() {
        return (orderId, logisticsStatus) -> {
        };
    }
}

package com.ecommerce.logistics.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ComponentScan(basePackages = "com.ecommerce.logistics.controller")
@EnableMethodSecurity
public class TestApplication {

    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http.securityContext(securityContext -> securityContext
                .securityContextRepository(new TestSecurityContextRepository()));
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }

    public static class TestSecurityContextRepository implements SecurityContextRepository {

        private static final ThreadLocal<Authentication> TEST_AUTH_HOLDER = new ThreadLocal<>();

        public static void setTestAuth(Authentication auth) { TEST_AUTH_HOLDER.set(auth); }
        public static void clearTestAuth() { TEST_AUTH_HOLDER.remove(); }

        @Override
        public SecurityContext loadContext(HttpRequestResponseHolder holder) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            Authentication auth = TEST_AUTH_HOLDER.get();
            if (auth != null) { context.setAuthentication(auth); }
            return context;
        }

        @Override
        public void saveContext(SecurityContext context, HttpServletRequest request,
                                HttpServletResponse response) {}

        @Override
        public boolean containsContext(HttpServletRequest request) {
            return TEST_AUTH_HOLDER.get() != null;
        }
    }
}

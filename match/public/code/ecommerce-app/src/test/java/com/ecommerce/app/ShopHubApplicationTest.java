package com.ecommerce.app;

import com.ecommerce.logistics.query.OrderLogisticsStatusUpdater;
import testsupport.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context loads successfully.
 *
 * <p>Known pre-existing production issues addressed here:
 * <ul>
 *   <li>Duplicate class names across modules (SecurityConfig,
 *       ReviewApprovedEventListener) fixed via bean naming and
 *       component scan exclusions</li>
 *   <li>Hibernate mapping error in ReviewAppend entity fixed</li>
 *   <li>Unimplemented {@code OrderLogisticsStatusUpdater} interface
 *       in logistics module mocked here</li>
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DisplayName("ShopHubApplication")
class ShopHubApplicationTest {

    @MockBean
    private OrderLogisticsStatusUpdater orderLogisticsStatusUpdater;

    @Test
    @DisplayName("should load Spring application context successfully")
    void contextLoads() {
    }
}

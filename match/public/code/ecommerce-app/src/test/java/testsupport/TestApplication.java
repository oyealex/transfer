package testsupport;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Minimal {@code @SpringBootConfiguration} for integration tests.
 * Placed in a package outside {@code com.ecommerce} so it is never
 * picked up by {@code ShopHubApplication}'s broad component scan.
 */
@EnableCaching
@EnableScheduling
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.ecommerce",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = DuplicateClassNameExcludeFilter.class))
@EntityScan(basePackages = "com.ecommerce")
public class TestApplication {
}

package com.ecommerce.blackbox.common;

import com.ecommerce.app.ShopHubApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract base class for all ecommerce blackbox REST tests.
 * <p>
 * Every concrete test class extending this base automatically:
 * <ul>
 *   <li>starts a fresh full ShopHub application on a random port with the {@code test} profile;</li>
 *   <li>uses a fresh test datasource / cache state for each test method;</li>
 *   <li>logs in the test-harness seeded admin user and stores the admin JWT for fixture setup;</li>
 *   <li>generates a unique {@code testRunId} so business keys never collide across tests.</li>
 * </ul>
 * <p>
 * Subclasses access {@link #apiClient}, {@link #testRunContext},
 * {@link #adminHeaders()}, and {@link #userHeaders(String)} to build
 * Given-When-Then scenarios without any direct database or service access.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ShopHubApplication.class, BlackboxHarnessConfig.class}
)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BlackboxTestBase {

    @LocalServerPort
    protected int port;

    protected ApiClient apiClient;
    protected TestRunContext testRunContext;

    @BeforeEach
    void setUpEach() {
        this.apiClient = new ApiClient(new TestRestTemplate(), port);
        String testRunId = UUID.randomUUID().toString().substring(0, 8);
        this.testRunContext = new TestRunContext(testRunId);

        String adminToken = loginSeededAdmin();
        testRunContext.setAdminToken(adminToken);
    }

    // ------------------------------------------------------------------
    // Shared header helpers
    // ------------------------------------------------------------------

    private String loginSeededAdmin() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", BlackboxHarnessConfig.ADMIN_EMAIL);
        body.put("password", BlackboxHarnessConfig.ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = apiClient.post("/api/v1/users/login", body, headers);
        assertEquals(200, response.getStatusCode().value(),
                "test-harness seeded admin should be able to login");
        return apiClient.readJsonPath(response, "$.token");
    }

    /**
     * Returns HTTP headers with the admin Bearer token set and Content-Type
     * application/json. Use for all fixture-setup calls that require ADMIN
     * authorization.
     */
    protected HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testRunContext.getAdminToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Returns HTTP headers with the given user Bearer token set and Content-Type
     * application/json. Use for user-authenticated REST calls within a test.
     *
     * @param token a JWT obtained via the login endpoint
     */
    protected HttpHeaders userHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

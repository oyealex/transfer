package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST fixture for user operations: register, activate, login, address management,
 * freeze/unfreeze, and profile retrieval.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class UserFixture extends BlackboxTestBase {

    public UserFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // User operations
    // ----------------------------------------------------------------

    /**
     * Registers a new user with a unique email and phone derived from the
     * {@link TestRunContext}.
     *
     * @param ctx per-test unique business-key context
     * @return parsed {@link RegisterResult} with userId, email, and activationToken
     */
    public RegisterResult registerUser(TestRunContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", ctx.uniqueEmail());
        body.put("phone", ctx.uniquePhone());
        body.put("password", "Password123!");
        body.put("nickname", "Tester");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = apiClient.post("/api/v1/users/register", body, headers);
        return parseRegisterResult(resp.getBody());
    }

    /**
     * Activates a user account using an email activation token.
     *
     * @param ctx             per-test context
     * @param activationToken the token from registration or notification email
     */
    public void activateUser(TestRunContext ctx, String activationToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", activationToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        apiClient.post("/api/v1/users/activate", body, headers);
    }

    /**
     * Logs in a user and returns the JWT token string.
     *
     * @param ctx      per-test context
     * @param email    the user's email
     * @param password the user's password (default "Password123!")
     * @return the JWT bearer token string
     */
    public String loginUser(TestRunContext ctx, String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = apiClient.post("/api/v1/users/login", body, headers);

        return parseLoginToken(resp.getBody());
    }

    /**
     * Convenience method that chains register, activate, and login into a single
     * call. Returns an {@link ActivatedUser} with the userId and JWT token.
     *
     * @param ctx per-test unique business-key context
     * @return ActivatedUser with userId and token
     */
    public ActivatedUser registerAndActivateUser(TestRunContext ctx) {
        RegisterResult regResult = registerUser(ctx);

        // If an activation token is present, attempt activation.
        String activationToken = regResult.getActivationToken();
        if (activationToken != null && !activationToken.isEmpty()) {
            try {
                activateUser(ctx, activationToken);
            } catch (Exception ignored) {
                // Activation may fail if user is already ACTIVE — acceptable.
            }
        }

        String token = loginUser(ctx, regResult.getEmail(), "Password123!");
        ActivatedUser result = new ActivatedUser();
        result.setUserId(regResult.getUserId());
        result.setToken(token);
        return result;
    }

    /**
     * Creates a default address for the given user.
     *
     * @param ctx       per-test context
     * @param userToken the user's JWT bearer token
     * @param province  e.g. "Guangdong"
     * @param city      e.g. "Shenzhen"
     * @param district  e.g. "Nanshan"
     * @param detail    e.g. "No.1 Tech Street, Apt 101"
     * @return parsed {@link AddressResult} containing the new addressId
     */
    public AddressResult createAddress(TestRunContext ctx, String userToken,
                                       String province, String city,
                                       String district, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("province", province);
        body.put("city", city);
        body.put("district", district);
        body.put("detail", detail);
        body.put("receiverName", "Receiver");
        body.put("receiverPhone", "13800009999");
        body.put("isDefault", true);

        ResponseEntity<String> resp = apiClient.post("/api/v1/users/addresses", body, userHeaders(userToken));
        return parseAddressResult(resp.getBody());
    }

    /**
     * Freezes a user account (admin operation).
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @param userId     the ID of the user to freeze
     * @return raw HTTP response
     */
    public ResponseEntity<String> freezeUser(TestRunContext ctx, String adminToken, Long userId) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        return apiClient.post("/api/v1/admin/users/" + userId + "/freeze", null, headers);
    }

    /**
     * Retrieves the current user's profile information.
     *
     * @param ctx       per-test context
     * @param userToken the user's JWT bearer token
     * @return raw HTTP response for assertion / extraction
     */
    public ResponseEntity<String> getCurrentUser(TestRunContext ctx, String userToken) {
        return apiClient.get("/api/v1/users/me", userHeaders(userToken));
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private RegisterResult parseRegisterResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            RegisterResult result = new RegisterResult();
            if (root.has("userId")) {
                result.setUserId(root.get("userId").asLong());
            }
            if (root.has("email")) {
                result.setEmail(root.get("email").asText());
            }
            if (root.has("activationToken")) {
                result.setActivationToken(root.get("activationToken").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RegisterResult from JSON: " + e.getMessage(), e);
        }
    }

    private String parseLoginToken(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            if (root.has("token")) {
                return root.get("token").asText();
            }
            throw new RuntimeException("Login response does not contain 'token' field");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse login token from JSON: " + e.getMessage(), e);
        }
    }

    private AddressResult parseAddressResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            AddressResult result = new AddressResult();
            if (root.has("addressId")) {
                result.setAddressId(root.get("addressId").asLong());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AddressResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Private header helpers
    // ----------------------------------------------------------------

    private static HttpHeaders bearerJsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }

    // ----------------------------------------------------------------
    // Result POJOs
    // ----------------------------------------------------------------

    /**
     * Result of a user registration call.
     */
    public static class RegisterResult {
        private Long userId;
        private String email;
        private String activationToken;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getActivationToken() { return activationToken; }
        public void setActivationToken(String activationToken) { this.activationToken = activationToken; }
    }

    /**
     * Result of the full register + activate + login chain.
     */
    public static class ActivatedUser {
        private Long userId;
        private String token;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    /**
     * Result of an address creation call.
     */
    public static class AddressResult {
        private Long addressId;

        public Long getAddressId() { return addressId; }
        public void setAddressId(Long addressId) { this.addressId = addressId; }
    }
}

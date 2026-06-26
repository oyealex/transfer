package com.ecommerce.blackbox.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Thin convenience wrapper around {@link TestRestTemplate} that prepends the
 * local server base URL and delegates to common HTTP verbs. All response bodies
 * are returned as raw {@link String} so callers can use
 * {@link #readJsonPath(ResponseEntity, String)} for assertions.
 */
public class ApiClient {

    private final TestRestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    /**
     * @param restTemplate the TestRestTemplate to delegate to (created externally)
     * @param port         the local server port obtained via {@code @LocalServerPort}
     */
    public ApiClient(TestRestTemplate restTemplate, int port) {
        this.restTemplate = restTemplate;
        this.baseUrl = "http://localhost:" + port;
        this.objectMapper = new ObjectMapper();
    }

    // ------------------------------------------------------------------
    // HTTP convenience methods
    // ------------------------------------------------------------------

    public ResponseEntity<String> post(String url, Object body, HttpHeaders headers) {
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(baseUrl + url, entity, String.class);
    }

    public ResponseEntity<String> get(String url, HttpHeaders headers) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(baseUrl + url, HttpMethod.GET, entity, String.class);
    }

    public ResponseEntity<String> put(String url, Object body, HttpHeaders headers) {
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(baseUrl + url, HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> delete(String url, HttpHeaders headers) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(baseUrl + url, HttpMethod.DELETE, entity, String.class);
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    /**
     * Extracts a value from the response body using a JSON path expression.
     * Supports dot-notation paths such as {@code "$.fieldName"} or
     * {@code "$.nested.field"}.
     *
     * @param response the HTTP response whose body contains JSON
     * @param jsonPath the JSON path (e.g. {@code "$.token"}, {@code "$.data.userId"})
     * @return the string value at the path, or {@code null} if the path does not exist
     * @throws RuntimeException if the response body is not valid JSON
     */
    public String readJsonPath(ResponseEntity<String> response, String jsonPath) {
        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
            for (String part : path.split("\\.")) {
                node = node.get(part);
                if (node == null) {
                    return null;
                }
            }
            return node.isTextual() ? node.asText() : node.toString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON from response body: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the underlying ObjectMapper for callers that need raw tree
     * navigation beyond what {@link #readJsonPath} provides.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}

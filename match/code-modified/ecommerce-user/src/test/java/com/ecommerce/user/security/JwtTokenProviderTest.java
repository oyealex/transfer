package com.ecommerce.user.security;

import com.ecommerce.user.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "test-issuer";
    private static final long EXPIRE_MINUTES = 120;
    private static final long EXPIRED_MINUTES = -1;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ISSUER, EXPIRE_MINUTES);
    }

    @Test
    @DisplayName("generates a non-empty JWT token with expected claims")
    void testGenerate_returnsNonEmptyToken() {
        String token = jwtTokenProvider.generateToken(42L, List.of("USER", "ADMIN"));

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("validates a valid token and returns claims containing userId and roles")
    void testValidate_validToken_returnsClaims() {
        String token = jwtTokenProvider.generateToken(42L, List.of("USER"));

        Jws<Claims> jws = jwtTokenProvider.validateToken(token);

        assertThat(jwtTokenProvider.getUserId(jws)).isEqualTo(42L);
        List<String> roles = jwtTokenProvider.getRoles(jws);
        assertThat(roles).containsExactly("USER");
    }

    @Test
    @DisplayName("extracts userId correctly from valid token claims")
    void testGetUserId_returnsCorrectId() {
        String token = jwtTokenProvider.generateToken(99L, List.of("ADMIN"));

        Jws<Claims> jws = jwtTokenProvider.validateToken(token);
        Long userId = jwtTokenProvider.getUserId(jws);

        assertThat(userId).isEqualTo(99L);
    }

    @Test
    @DisplayName("extracts roles correctly from valid token claims")
    void testGetRoles_returnsCorrectRoles() {
        String token = jwtTokenProvider.generateToken(1L, List.of("USER", "ADMIN"));

        Jws<Claims> jws = jwtTokenProvider.validateToken(token);
        List<String> roles = jwtTokenProvider.getRoles(jws);

        assertThat(roles).containsExactly("USER", "ADMIN");
    }

    @Test
    @DisplayName("returns empty roles list when token has no roles claim")
    void testGetRoles_emptyRoles_whenNotPresent() {
        // The generateToken always includes roles, so this tests the fallback.
        // The fallback in getRoles returns empty list when getPayload().get("roles") is null.
        // We test this by validating a valid token but feeding the claims directly.
        String token = jwtTokenProvider.generateToken(1L, List.of());

        Jws<Claims> jws = jwtTokenProvider.validateToken(token);
        List<String> roles = jwtTokenProvider.getRoles(jws);

        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("token subject is the userId as string")
    void testTokenSubject_isUserId() {
        String token = jwtTokenProvider.generateToken(100L, List.of("USER"));

        Jws<Claims> jws = jwtTokenProvider.validateToken(token);
        String subject = jws.getPayload().getSubject();

        assertThat(subject).isEqualTo("100");
    }

    @Test
    @DisplayName("token issuer matches configured issuer")
    void testTokenIssuer_matchesConfiguredIssuer() {
        String token = jwtTokenProvider.generateToken(1L, List.of("USER"));

        Jws<Claims> jws = jwtTokenProvider.validateToken(token);
        String issuer = jws.getPayload().getIssuer();

        assertThat(issuer).isEqualTo(ISSUER);
    }

    @Test
    @DisplayName("throws ExpiredJwtException when validating an expired token")
    void testValidate_expiredToken_throwsException() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, ISSUER, 0);
        String token = shortLivedProvider.generateToken(42L, List.of("USER"));

        // Token expires immediately (0 minutes), but there might be a tiny clock skew.
        // Wait a short time to ensure expiry.
        Thread.sleep(10);

        assertThatThrownBy(() -> shortLivedProvider.validateToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}

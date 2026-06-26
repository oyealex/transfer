package com.ecommerce.common.ratelimit;

import com.ecommerce.common.exception.RateLimitException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitAspect")
class RateLimitAspectTest {

    private RateLimitAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect();
    }

    /**
     * Creates an anonymous RateLimit annotation with the given parameters.
     */
    private static RateLimit createRateLimit(String key, int permitsPerMinute) {
        return new RateLimit() {
            @Override
            public String key() { return key; }

            @Override
            public int permitsPerMinute() { return permitsPerMinute; }

            @Override
            public Class<? extends Annotation> annotationType() { return RateLimit.class; }
        };
    }

    @Test
    @DisplayName("allows method execution when request count is within the rate limit")
    void testWithinLimit_requestsSucceed() throws Throwable {
        RateLimit rateLimit = createRateLimit("api-user-123", 100);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("blocks requests and throws RateLimitException when the limit is exceeded")
    void testExceedLimit_returns429() throws Throwable {
        RateLimit rateLimit = createRateLimit("heavy-user", 1);

        // First call within limit
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.enforceRateLimit(joinPoint, rateLimit);
        verify(joinPoint).proceed();

        // Second call exceeds limit — proceed() should not be called again
        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Too many requests for key: heavy-user");
    }

    @Test
    @DisplayName("rate limits for different keys are independent of each other")
    void testDifferentKeys_independentLimits() throws Throwable {
        RateLimit limitKeyA = createRateLimit("key-alpha", 1);
        RateLimit limitKeyB = createRateLimit("key-beta", 1);

        // Key A first call: allowed
        when(joinPoint.proceed()).thenReturn("A1");
        aspect.enforceRateLimit(joinPoint, limitKeyA);

        // Key B first call: still allowed (independent limit)
        when(joinPoint.proceed()).thenReturn("B1");
        aspect.enforceRateLimit(joinPoint, limitKeyB);

        // Key A second call: exceeded
        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, limitKeyA))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("key-alpha");

        // Key B second call: also exceeded
        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, limitKeyB))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("key-beta");
    }

    @Test
    @DisplayName("resolves key to method signature when RateLimit key is empty string")
    void testEmptyKey_defaultsToClassNameAndMethodName() throws Throwable {
        RateLimit rateLimit = createRateLimit("", 100);
        when(joinPoint.getSignature()).thenReturn(methodSignature);

        java.lang.reflect.Method testMethod = String.class.getMethod("length");
        when(methodSignature.getMethod()).thenReturn(testMethod);

        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("resolves key as literal string when it does not contain SpEL expressions")
    void testLiteralKey_usedAsIs() throws Throwable {
        RateLimit rateLimit = createRateLimit("static-literal-key", 100);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("proceed is not called when rate limit is exceeded")
    void testExceededLimit_neverCallsProceed() throws Throwable {
        RateLimit rateLimit = createRateLimit("strict-limit", 0);

        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitException.class);

        verifyNoInteractions(joinPoint);
    }

    @Test
    @DisplayName("multiple requests within limit all proceed successfully")
    void testMultipleRequestsWithinLimit() throws Throwable {
        RateLimit rateLimit = createRateLimit("bulk-user", 5);
        when(joinPoint.proceed()).thenReturn("pass");

        for (int i = 0; i < 5; i++) {
            Object result = aspect.enforceRateLimit(joinPoint, rateLimit);
            assertThat(result).isEqualTo("pass");
        }
    }

    @Test
    @DisplayName("spel expression key that cannot be resolved falls back to using the raw key string")
    void testUnresolvableSpelExpression_fallsBackToRawKey() throws Throwable {
        RateLimit rateLimit = createRateLimit("#nonexistentVar", 100);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getSignature()).thenReturn(methodSignature);

        java.lang.reflect.Method method = Object.class.getMethod("toString");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        // When parameter names can't be discovered for the toString method,
        // the expression resolution may fail, but it should fall back to using
        // the raw key "#nonexistentVar"
        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);
        assertThat(result).isEqualTo("ok");
    }
}

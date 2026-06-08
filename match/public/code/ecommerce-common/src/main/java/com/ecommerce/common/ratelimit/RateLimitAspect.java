package com.ecommerce.common.ratelimit;

import com.ecommerce.common.dto.ApiError;
import com.ecommerce.common.exception.RateLimitException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect that intercepts methods annotated with @RateLimit and enforces
 * a sliding-window rate limit using an in-memory ConcurrentHashMap.
 *
 * <p>When the limit is exceeded, the method is not invoked and a 429 response
 * with an ApiError body is returned instead.
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private static final long WINDOW_MS = 60_000; // 1 minute window
    private final Map<String, LinkedList<Long>> rateLimitStore = new ConcurrentHashMap<>();
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(rateLimit.key(), joinPoint);
        int permits = rateLimit.permitsPerMinute();

        if (isAllowed(key, permits)) {
            return joinPoint.proceed();
        }

        log.warn("Rate limit exceeded: key={}, permitsPerMinute={}", key, permits);
        throw new RateLimitException("Too many requests for key: " + key);
    }

    /**
     * Resolves the rate-limit key. If the key contains a SpEL expression
     * (prefixed with #), it is evaluated against the method parameters.
     */
    private String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return signature.getMethod().getDeclaringClass().getSimpleName()
                    + "." + signature.getMethod().getName();
        }

        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            EvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();

            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            Expression expression = parser.parseExpression(keyExpression);
            Object resolved = expression.getValue(context);
            return resolved != null ? resolved.toString() : keyExpression;
        } catch (Exception e) {
            log.warn("Failed to resolve SpEL expression '{}', using raw key", keyExpression, e);
            return keyExpression;
        }
    }

    /**
     * Sliding-window check: removes timestamps older than 1 minute, then checks
     * if the current request count is under the limit.
     */
    private boolean isAllowed(String key, int permits) {
        long now = System.currentTimeMillis();
        LinkedList<Long> timestamps = rateLimitStore.computeIfAbsent(key, k -> new LinkedList<>());

        synchronized (timestamps) {
            // Evict timestamps outside the window
            while (!timestamps.isEmpty() && now - timestamps.getFirst() > WINDOW_MS) {
                timestamps.removeFirst();
            }

            if (timestamps.size() < permits) {
                timestamps.addLast(now);
                return true;
            }

            return false;
        }
    }
}

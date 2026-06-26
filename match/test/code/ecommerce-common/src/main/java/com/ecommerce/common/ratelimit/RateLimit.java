package com.ecommerce.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for applying local rate limiting to controller methods.
 * Uses a sliding-window algorithm implemented in RateLimitAspect.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * The key used to identify the rate limit bucket.
     * Supports SpEL expressions for dynamic keys (e.g., "#username").
     */
    String key() default "";

    /**
     * Maximum number of permitted requests per minute.
     */
    int permitsPerMinute() default 60;
}

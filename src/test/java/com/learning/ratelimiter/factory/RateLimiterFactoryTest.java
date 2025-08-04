package com.learning.ratelimiter.factory;

import com.learning.ratelimiter.config.RateLimiterProperties;
import com.learning.ratelimiter.service.RateLimitService;
import com.learning.ratelimiter.strategy.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.learning.ratelimiter.strategy.RateLimitingAlgorithm.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterFactoryTest {
    @Test
    void shouldCreateFixedWindowStrategy() {
        // Given
        RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(100, 60000);
        RateLimiterFactory factory = new RateLimiterFactory(FIXED_WINDOW, config);
        // When / Then
      // When / Then: only the createStrategy() call is expected to throw
//                IllegalStateException ex = assertThrows(
//                IllegalStateException.class,
//                () -> factory.createStrategy()
//        );
//
//        assertEquals("Unexpected value: SLIDING_WINDOW", ex.getMessage());
        // When
        RateLimitingStrategy strategy = factory.createStrategy();

        // Then
        assertThat(strategy).isInstanceOf(FixedWindowStrategy.class);
        assertThat(strategy.allowRequest("client-1")).isTrue();
    }
    @Test
    void shouldCreateSlidingWindowStrategy() {
        // Given
        RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(100, 60000);
        RateLimiterFactory factory = new RateLimiterFactory(SLIDING_WINDOW, config);
        // When / Then
        // When / Then: only the createStrategy() call is expected to throw
//                IllegalStateException ex = assertThrows(
//                IllegalStateException.class,
//                () -> factory.createStrategy()
//        );
//
//        assertEquals("Unexpected value: SLIDING_WINDOW", ex.getMessage());
        // When
        RateLimitingStrategy strategy = factory.createStrategy();

        // Then
        assertThat(strategy).isInstanceOf(SlidingWindowStrategy.class);
        assertThat(strategy.allowRequest("client-1")).isTrue();
    }
    @Test
    void shouldCreateTokenBucketStrategy() {
        // Given
        RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(100, 60000);
        RateLimiterFactory factory = new RateLimiterFactory(TOKEN_BUCKET, config);

        // When
        RateLimitingStrategy strategy = factory.createStrategy();

        // Then
        assertThat(strategy).isInstanceOf(TokenBucketStrategy.class);
        assertThat(strategy.allowRequest("client-1")).isTrue();
    }
    @Test
    void shouldCreateLeakyBucketStrategy() {
        // Given
        RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(100, 60000);
        RateLimiterFactory factory = new RateLimiterFactory(LEAKY_BUCKET, config);

        // When
        RateLimitingStrategy strategy = factory.createStrategy();

        // Then
        assertThat(strategy).isInstanceOf(LeakyBucketStrategy.class);
        assertThat(strategy.allowRequest("client-1")).isTrue();
    }
    @Test
    void shouldMeasureRateLimiterPerformanceDirectly() {
        RateLimiterProperties properties=new RateLimiterProperties();
        RateLimitService service = new RateLimitService(properties);
        HttpServletRequest mockRequest= Mockito.mock(HttpServletRequest.class);
        // Configure the mock to return realistic values
        Mockito.when(mockRequest.getRequestURI()).thenReturn("/api/hello");
        Mockito.when(mockRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        Mockito.when(mockRequest.getHeader("X-API-Key")).thenReturn(null);
        Mockito.when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            service.checkRateLimit(mockRequest); // Direct method call
        }
        long endTime = System.nanoTime();

        double avgDuration = (endTime - startTime) / 1_000_000.0 / 1000; // Convert to ms per request
        System.out.println("Direct rate limiter performance: " + avgDuration + " ms");

        assertThat(avgDuration).isLessThan(1.0); // Should be under 1ms
    }
}

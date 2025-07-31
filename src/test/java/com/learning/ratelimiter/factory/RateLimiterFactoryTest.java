package com.learning.ratelimiter.factory;

import com.learning.ratelimiter.strategy.FixedWindowStrategy;
import com.learning.ratelimiter.strategy.RateLimitingStrategy;
import com.learning.ratelimiter.strategy.SlidingWindowStrategy;
import org.junit.jupiter.api.Test;

import static com.learning.ratelimiter.strategy.RateLimitingAlgorithm.FIXED_WINDOW;
import static com.learning.ratelimiter.strategy.RateLimitingAlgorithm.SLIDING_WINDOW;
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
}

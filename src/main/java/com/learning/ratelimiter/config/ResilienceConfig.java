package com.learning.ratelimiter.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker rateLimiterCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("rateLimiterCircuitBreaker");
    }

    @Bean
    public Retry rateLimiterRetry(RetryRegistry registry) {
        return registry.retry("rateLimiterRetry");
    }
}

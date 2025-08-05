package com.learning.ratelimiter.health;

import com.learning.ratelimiter.service.RateLimitService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RateLimiterHealthIndicator implements HealthIndicator {

    private final RateLimitService rateLimitService;

    public RateLimiterHealthIndicator(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public Health health() {
        try {
            // Get statistics from your rate limiter
            Map<String, Object> stats = rateLimitService.getStatistics();

            // Check if rate limiter is healthy
            int totalEndpoints = (Integer) stats.get("totalEndpoints");
            int configuredEndpoints = (Integer) stats.get("configuredEndpoints");

            if (totalEndpoints >= configuredEndpoints) {
                return Health.up()
                        .withDetail("status", "Rate limiter operational")
                        .withDetail("totalEndpoints", totalEndpoints)
                        .withDetail("configuredEndpoints", configuredEndpoints)
                        .withDetail("algorithm", stats.get("defaultAlgorithm"))
                        .withDetail("clientIdStrategy", stats.get("clientIdStrategy"))
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "Some endpoints not initialized")
                        .withDetail("totalEndpoints", totalEndpoints)
                        .withDetail("configuredEndpoints", configuredEndpoints)
                        .build();
            }

        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "Rate limiter check failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
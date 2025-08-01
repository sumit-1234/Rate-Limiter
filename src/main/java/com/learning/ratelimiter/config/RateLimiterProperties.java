package com.learning.ratelimiter.config;

import com.learning.ratelimiter.strategy.RateLimitingAlgorithm;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "rate-limiter")
@Component
public class RateLimiterProperties {

    // Default settings
    private int defaultMaxRequests = 100;
    private long defaultTimeWindowMs = 60000;
    private RateLimitingAlgorithm defaultAlgorithm = RateLimitingAlgorithm.FIXED_WINDOW;

    // Client ID strategy configuration
    private String clientIdStrategy = "IP_ADDRESS";
    private String apiKeyHeader = "X-API-Key";
    private String userIdHeader = "X-User-ID";

    // Per-endpoint configuration
    private Map<String, EndpointConfig> endpoints = new HashMap<>();

    // Getters and Setters
    public int getDefaultMaxRequests() { return defaultMaxRequests; }
    public void setDefaultMaxRequests(int defaultMaxRequests) { this.defaultMaxRequests = defaultMaxRequests; }

    public long getDefaultTimeWindowMs() { return defaultTimeWindowMs; }
    public void setDefaultTimeWindowMs(long defaultTimeWindowMs) { this.defaultTimeWindowMs = defaultTimeWindowMs; }

    public RateLimitingAlgorithm getDefaultAlgorithm() { return defaultAlgorithm; }
    public void setDefaultAlgorithm(RateLimitingAlgorithm defaultAlgorithm) { this.defaultAlgorithm = defaultAlgorithm; }

    public String getClientIdStrategy() { return clientIdStrategy; }
    public void setClientIdStrategy(String clientIdStrategy) { this.clientIdStrategy = clientIdStrategy; }

    public String getApiKeyHeader() { return apiKeyHeader; }
    public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }

    public String getUserIdHeader() { return userIdHeader; }
    public void setUserIdHeader(String userIdHeader) { this.userIdHeader = userIdHeader; }

    public Map<String, EndpointConfig> getEndpoints() { return endpoints; }
    public void setEndpoints(Map<String, EndpointConfig> endpoints) { this.endpoints = endpoints; }

    // Inner class for endpoint-specific configuration
    public static class EndpointConfig {
        private int maxRequests = 100;
        private long timeWindowMs = 60000;
        private RateLimitingAlgorithm algorithm = RateLimitingAlgorithm.FIXED_WINDOW;
        private boolean enabled = true;

        // Getters and Setters
        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

        public long getTimeWindowMs() { return timeWindowMs; }
        public void setTimeWindowMs(long timeWindowMs) { this.timeWindowMs = timeWindowMs; }

        public RateLimitingAlgorithm getAlgorithm() { return algorithm; }
        public void setAlgorithm(RateLimitingAlgorithm algorithm) { this.algorithm = algorithm; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
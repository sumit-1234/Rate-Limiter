package com.learning.ratelimiter.service;

import com.learning.ratelimiter.config.RateLimiterProperties;
import com.learning.ratelimiter.factory.RateLimiterFactory;
import com.learning.ratelimiter.strategy.RateLimitingAlgorithm;
import com.learning.ratelimiter.strategy.RateLimitingStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class RateLimitService {
    private static final Logger logger= LoggerFactory.getLogger(RateLimitService.class);
    private static final Logger performanceLogger = LoggerFactory.getLogger("com.learning.ratelimiter.performance");
    private static final Logger securityLogger = LoggerFactory.getLogger("com.learning.ratelimiter.security");
    private final RateLimiterProperties properties;
    private final Map<String, RateLimitingStrategy> endpointLimiters;
    public RateLimitService(RateLimiterProperties properties)
    {
        this.properties=properties;
        this.endpointLimiters=new ConcurrentHashMap<>();
        logger.info("RateLimitService initialized with {} endpoint configurations",
                properties.getEndpoints().size());
    }
    private final Map<String, String> normalizedEndpointCache = new ConcurrentHashMap<>();

    private String getNormalizedEndpoint(String endpoint) {
        return normalizedEndpointCache.computeIfAbsent(endpoint,
                ep -> ep.replaceAll("/", ""));
    }
    @PostConstruct
    public void initializeRateLimiters() {
        // Pre-create rate limiters for all configured endpoints
        properties.getEndpoints().keySet().forEach(endpoint -> {
            // Create rate limiter for the request format: "/api/hello"
            String requestFormat = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
            getRateLimiterForEndpoint(requestFormat);
            logger.debug("Pre-initialized rate limiter for: {}", requestFormat);
        });
        logger.info("Pre-initialized {} rate limiters", endpointLimiters.size());
    }
    public RateLimitResult checkRateLimit(HttpServletRequest request) {
        long totalStart = System.nanoTime();
        try {
            // 1. Extract client ID (IP address or custom header)
            long step1Start = System.nanoTime();
            String clientId = extractClientId(request);
            long step1Duration = System.nanoTime() - step1Start;

            // 2. Get endpoint path
            long step2Start = System.nanoTime();
            String endpoint = request.getRequestURI();
            long step2Duration = System.nanoTime() - step2Start;

            // 3. Get or create rate limiter for this endpoint
            long step3Start = System.nanoTime();
            RateLimitingStrategy rateLimiter = getRateLimiterForEndpoint(endpoint);
            long step3Duration = System.nanoTime() - step3Start;

            // 4. Check if request should be allowed
            long step4Start = System.nanoTime();
            boolean allowed = rateLimiter.allowRequest(clientId);
            long step4Duration = System.nanoTime() - step4Start;

            // 5. Get remaining requests info
            long step5Start = System.nanoTime();
            long remainingRequests = rateLimiter.getRemainingRequests(clientId);
            long step5Duration = System.nanoTime() - step5Start;

            // 6. Get Algorithm info
            long step6Start = System.nanoTime();
            String algorithm = getAlgorithmForEndpoint(endpoint).name();
            long step6Duration = System.nanoTime() - step6Start;

            long totalDuration = System.nanoTime() - totalStart;

            // Log timing breakdown (only for first few requests to avoid spam)
            if (totalDuration > 1_000_000) { // Log if > 1ms
                performanceLogger.warn("Slow rate limit check: {}ms for client={}, endpoint={}",
                        totalDuration / 1_000_000.0, clientId, endpoint);
            }
        RateLimitResult result = new RateLimitResult(
                allowed,
                remainingRequests,
                algorithm,
                clientId,
                endpoint
        );

//        logger.debug("Rate limit check: clientId={}, endpoint={}, allowed={}, remaining={}",
//                clientId, endpoint, allowed, remainingRequests);
            // Add rate limiting context to MDC for this request
            MDC.put("clientId", clientId);
            MDC.put("endpoint", endpoint);
            MDC.put("algorithm", algorithm);
            MDC.put("allowed", String.valueOf(allowed));
            MDC.put("remaining", String.valueOf(remainingRequests));

// Enhanced logging with decision reasoning
            if (allowed) {
                logger.info("✅ ALLOWED: {} - Remaining: {}/{} using {}",
                        clientId, remainingRequests, getMaxRequestsForEndpoint(endpoint), algorithm);
            } else {
                securityLogger.warn("Rate limit violation: client={}, endpoint={}, algorithm={}",
                        clientId, endpoint, algorithm);
            }

        return result;
    }catch(Exception e){
            logger.error("Error during rate limit check", e);
            // Fail open - allow request if rate limiting fails
            return new RateLimitResult(true, -1, "ERROR", "unknown", request.getRequestURI());
        }
    }
    private int getMaxRequestsForEndpoint(String endpoint) {
        String normalizedEndpoint = getNormalizedEndpoint(endpoint);

        RateLimiterProperties.EndpointConfig config = null;
        for (Map.Entry<String, RateLimiterProperties.EndpointConfig> entry : properties.getEndpoints().entrySet()) {
            String normalizedKey = entry.getKey().replaceAll("/", "");
            if (normalizedKey.equals(normalizedEndpoint)) {
                config = entry.getValue();
                break;
            }
        }

        return (config != null && config.isEnabled()) ?
                config.getMaxRequests() : properties.getDefaultMaxRequests();
    }

    /**
     * Get algorithm for endpoint (for response headers)
     */
    private RateLimitingAlgorithm getAlgorithmForEndpoint(String endpoint) {
        String normalizedEndpoint = getNormalizedEndpoint(endpoint);
        logger.info(
                "normalize {}",normalizedEndpoint
        );
        RateLimiterProperties.EndpointConfig config = properties.getEndpoints().get(normalizedEndpoint);
        return (config != null && config.isEnabled()) ?
                config.getAlgorithm() : properties.getDefaultAlgorithm();
    }

    /**
     * Extract client ID based on configured strategy
     */
    private String extractClientId(HttpServletRequest request) {
        String clientIdStrategy = properties.getClientIdStrategy();

        switch (clientIdStrategy.toUpperCase()) {
            case "IP_ADDRESS":
                return extractIpAddress(request);

            case "API_KEY":
                String apiKey = request.getHeader(properties.getApiKeyHeader());
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    return "API_" + apiKey;
                }
                // Fallback to IP if no API key
                logger.warn("No API key found in header '{}', falling back to IP address",
                        properties.getApiKeyHeader());
                return extractIpAddress(request);

            case "COMBINED":
                String apiKeyValue = request.getHeader(properties.getApiKeyHeader());
                String ipAddress = extractIpAddress(request);

                if (apiKeyValue != null && !apiKeyValue.trim().isEmpty()) {
                    return "API_" + apiKeyValue + "_IP_" + ipAddress;
                } else {
                    return "IP_" + ipAddress;
                }

            case "USER_ID":
                String userId = request.getHeader(properties.getUserIdHeader());
                if (userId != null && !userId.trim().isEmpty()) {
                    return "USER_" + userId;
                }
                // Fallback to IP if no user ID
                logger.warn("No user ID found in header '{}', falling back to IP address",
                        properties.getUserIdHeader());
                return extractIpAddress(request);

            default:
                logger.warn("Unknown client ID strategy '{}', falling back to IP address", clientIdStrategy);
                return extractIpAddress(request);
        }
    }

    /**
     * Extract IP address with proxy support
     */
    private String extractIpAddress(HttpServletRequest request) {
        // Check for IP address from various headers (for proxy support)
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "X-Original-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return "IP_" + ip;
            }
        }

        // Fall back to remote address
        return "IP_" + request.getRemoteAddr();
    }


    /**
     * Get rate limiter for specific endpoint (create if doesn't exist)
     */
    private RateLimitingStrategy getRateLimiterForEndpoint(String endpoint) {
        // Try exact match first
        System.out.println("Available keys: " + endpointLimiters.keySet());
        System.out.println("Looking up: " + endpoint);

        return endpointLimiters.computeIfAbsent(endpoint, this::createRateLimiterForEndpoint);

    }
    /**
     * Create rate limiter using factory with endpoint-specific or default configuration
     */
    private RateLimitingStrategy createRateLimiterForEndpoint(String endpoint) {
        logger.info("Available keys: {}", properties.getEndpoints().keySet());
        logger.info("Looking up endpoint: {}", endpoint);

        // Normalize by removing all '/'
        String normalizedEndpoint = getNormalizedEndpoint(endpoint);

        // Also normalize keys in the config
        RateLimiterProperties.EndpointConfig matchedConfig = null;
        for (Map.Entry<String, RateLimiterProperties.EndpointConfig> entry : properties.getEndpoints().entrySet()) {
            String normalizedKey = entry.getKey().replaceAll("/", "");
            if (normalizedKey.equals(normalizedEndpoint)) {
                matchedConfig = entry.getValue();
                break;
            }
        }

        logger.info("Matched Config: {}", matchedConfig);

        int maxRequests;
        long timeWindowMs;
        RateLimitingAlgorithm algorithm;

        if (matchedConfig != null && matchedConfig.isEnabled()) {
            maxRequests = matchedConfig.getMaxRequests();
            timeWindowMs = matchedConfig.getTimeWindowMs();
            algorithm = matchedConfig.getAlgorithm();

            logger.info("Creating rate limiter for endpoint '{}' with custom config: {}req/{}ms using {}",
                    endpoint, maxRequests, timeWindowMs, algorithm);
        } else {
            maxRequests = properties.getDefaultMaxRequests();
            timeWindowMs = properties.getDefaultTimeWindowMs();
            algorithm = properties.getDefaultAlgorithm();

            logger.info("Creating default rate limiter for endpoint '{}' with default config: {}req/{}ms using {}",
                    endpoint, maxRequests, timeWindowMs, algorithm);
        }

        RateLimiterFactory.Configuration factoryConfig =
                new RateLimiterFactory.Configuration(maxRequests, timeWindowMs);
        RateLimiterFactory factory = new RateLimiterFactory(algorithm, factoryConfig);

        return factory.createStrategy();
    }

    /**
     * Reset rate limits for a specific client (useful for testing/admin)
     */
    public void resetRateLimitForClient(String clientId, String endpoint) {
        RateLimitingStrategy rateLimiter = endpointLimiters.get(endpoint);
        if (rateLimiter != null) {
            rateLimiter.reset(clientId);
            logger.info("Reset rate limit for client '{}' on endpoint '{}'", clientId, endpoint);
        }
    }
    /**
     * Get current statistics for monitoring
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalEndpoints", endpointLimiters.size());
        stats.put("configuredEndpoints", properties.getEndpoints().size());
        stats.put("defaultAlgorithm", properties.getDefaultAlgorithm().name());
        stats.put("clientIdStrategy", properties.getClientIdStrategy());
        return stats;
    }
    @PostConstruct  // Add this import: import jakarta.annotation.PostConstruct;
    public void debugConfiguration() {
        logger.info("=== RATE LIMITER DEBUG ===");
        logger.info("Default max requests: {}", properties.getDefaultMaxRequests());
        logger.info("Default algorithm: {}", properties.getDefaultAlgorithm());
        logger.info("Client ID strategy: {}", properties.getClientIdStrategy());
        logger.info("Configured endpoints: {}", properties.getEndpoints().size());

        properties.getEndpoints().forEach((endpoint, config) -> {
            logger.info("Endpoint '{}': maxRequests={}, algorithm={}, enabled={}",
                    endpoint, config.getMaxRequests(), config.getAlgorithm(), config.isEnabled());
        });
        logger.info("=== END DEBUG ===");
    }
    /**
     * Result of rate limit check
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingRequests;
        private final String algorithm;
        private final String clientId;
        private final String endpoint;

        public RateLimitResult(boolean allowed, long remainingRequests, String algorithm,
                               String clientId, String endpoint) {
            this.allowed = allowed;
            this.remainingRequests = remainingRequests;
            this.algorithm = algorithm;
            this.clientId = clientId;
            this.endpoint = endpoint;
        }

        public boolean isAllowed() { return allowed; }
        public long getRemainingRequests() { return remainingRequests; }
        public String getAlgorithm() { return algorithm; }
        public String getClientId() { return clientId; }
        public String getEndpoint() { return endpoint; }

        @Override
        public String toString() {
            return String.format("RateLimitResult{allowed=%s, remaining=%d, algorithm=%s, client=%s, endpoint=%s}",
                    allowed, remainingRequests, algorithm, clientId, endpoint);
        }
    }
}

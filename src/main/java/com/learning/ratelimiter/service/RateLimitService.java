package com.learning.ratelimiter.service;

import com.learning.ratelimiter.config.RateLimiterProperties;
import com.learning.ratelimiter.exception.RateLimiterExceptions;
import com.learning.ratelimiter.factory.RateLimiterFactory;
import com.learning.ratelimiter.strategy.RateLimitingAlgorithm;
import com.learning.ratelimiter.strategy.RateLimitingStrategy;
import io.micrometer.core.instrument.Timer;
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
    private final RateLimitMetricsService metricsService;
    public RateLimitService(RateLimiterProperties properties, RateLimitMetricsService metricsService)
    {
        this.properties=properties;
        this.metricsService=metricsService;
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
        // Start timing
        Timer.Sample timerSample = metricsService.startTimer();
        long totalStart = System.nanoTime();
        // 2. Get endpoint path

        String endpoint = request.getRequestURI();
        // 1. Extract client ID (IP address or custom header)

        String clientId = extractClientId(request);

        try {


            // 3. Get or create rate limiter for this endpoint

            RateLimitingStrategy rateLimiter = getRateLimiterForEndpoint(endpoint);


            // 4. Check if request should be allowed

            boolean allowed = rateLimiter.allowRequest(clientId);


            // 5. Get remaining requests info

            long remainingRequests = rateLimiter.getRemainingRequests(clientId);


            // 6. Get Algorithm info

            String algorithm = getAlgorithmForEndpoint(endpoint).name();

            metricsService.recordRequest(allowed, endpoint, algorithm);
            metricsService.recordClientActivity(clientId,endpoint);
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
    }catch (RateLimiterExceptions.ConfigurationException e) {
            logger.error("Configuration error for endpoint {}: {}", endpoint, e.getMessage());
            return createFailSafeResult(clientId, endpoint, "CONFIG_ERROR");

        } catch (RateLimiterExceptions.RateLimiterUnavailableException e) {
            logger.error("Rate limiter unavailable for {}: {}", endpoint, e.getMessage());
            return createFailSafeResult(clientId, endpoint, "UNAVAILABLE");

        } catch (Exception e) {
            logger.error("Unexpected error in rate limiter", e);
            return createFailSafeResult(clientId, endpoint, "ERROR");

        } finally {
            timerSample.stop(metricsService.getRateLimitCheckTimer());
        }
    }
    private RateLimitResult createFailSafeResult(String clientId, String endpoint, String errorType) {
        // Read strategy from config
        String strategy = properties.getFailSafeStrategy(); // You'd need to add this property

        boolean allowed = "ALLOW".equalsIgnoreCase(strategy); // Default to ALLOW if not configured

        logger.warn("Fail-safe {} activated for client={}, endpoint={}, reason={}",
                allowed ? "ALLOW" : "DENY", clientId, endpoint, errorType);

        return new RateLimitResult(allowed, -1, errorType, clientId, endpoint);
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
        try {
            if (properties == null) {
                logger.error("RateLimiterProperties is null, using fallback");
                return "IP_" + request.getRemoteAddr();
            }
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
        }catch(Exception e){
            // NEVER throw - always return a safe fallback
            logger.error("Error extracting client ID, using IP fallback: {}", e.getMessage());
            try {
                return "IP_" + request.getRemoteAddr();
            } catch (Exception fallbackError) {
                // Even the fallback failed!
                logger.error("Even IP fallback failed: {}", fallbackError.getMessage());
                return "UNKNOWN_CLIENT_" + System.currentTimeMillis();
            }
        }
        }

    /**
     * Extract IP address with proxy support
     */
    private String extractIpAddress(HttpServletRequest request) {
        try {
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
        }catch(Exception e){
            logger.error("Error extracting IP address: {}", e.getMessage());
            return "UNKNOWN_IP_" + System.currentTimeMillis();
        }
    }


    /**
     * Get rate limiter for specific endpoint (create if doesn't exist)
     */
    private RateLimitingStrategy getRateLimiterForEndpoint(String endpoint) {
    try {
        return endpointLimiters.computeIfAbsent(endpoint, this::createRateLimiterForEndpoint);
    }catch(Exception e){
        logger.error("Failed to get rate limiter for {}, using default: {}", endpoint, e.getMessage());
        return createDefaultRateLimiter(); // Simple fallback
    }
    }
    private RateLimitingStrategy createDefaultRateLimiter() {
        try {
            // Use default configuration when endpoint-specific config fails
            RateLimiterFactory.Configuration defaultConfig =
                    new RateLimiterFactory.Configuration(
                            properties.getDefaultMaxRequests(),
                            properties.getDefaultTimeWindowMs()
                    );

            RateLimiterFactory factory = new RateLimiterFactory(
                    properties.getDefaultAlgorithm(),
                    defaultConfig
            );

            logger.info("Created default rate limiter: {}req/{}ms using {}",
                    properties.getDefaultMaxRequests(),
                    properties.getDefaultTimeWindowMs(),
                    properties.getDefaultAlgorithm());

            return factory.createStrategy();

        } catch (Exception e) {
            logger.error("Even default rate limiter creation failed: {}", e.getMessage());

            // Last resort: Create a very basic rate limiter
            RateLimiterFactory.Configuration emergencyConfig =
                    new RateLimiterFactory.Configuration(100, 60000); // 100 req/min

            RateLimiterFactory emergencyFactory = new RateLimiterFactory(
                    RateLimitingAlgorithm.FIXED_WINDOW,
                    emergencyConfig
            );

            return emergencyFactory.createStrategy();
        }
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

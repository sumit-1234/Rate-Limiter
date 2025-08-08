package com.learning.ratelimiter.service;

import com.learning.ratelimiter.resilience.DegradationStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ResilientRateLimitService {
    private static final Logger logger= LoggerFactory.getLogger(ResilientRateLimitService.class);

    private final RateLimitService rateLimitService;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientRateLimitService(RateLimitService rateLimitService,
                                     CircuitBreaker circuitBreaker,
                                     Retry retry) {
        this.rateLimitService = rateLimitService;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public RateLimitService.RateLimitResult checkRateLimit(HttpServletRequest request) {
        // Wrap with circuit breaker and retry
        Supplier<RateLimitService.RateLimitResult> supplier =
                CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                        rateLimitService.checkRateLimit(request)
                );

        supplier = Retry.decorateSupplier(retry, supplier);

        try {
            return supplier.get();
        } catch (Throwable e) {
            // Circuit is open or all retries failed - graceful degradation
            return createEmergencyFallback(request, e);
        }
    }

    private RateLimitService.RateLimitResult createEmergencyFallback(HttpServletRequest request, Throwable e) {
        String clientId = extractFallbackClientId(request);
        String endpoint = request.getRequestURI();

        // Determine strategy based on error type
        DegradationStrategy strategy = determineDegradationStrategy(e, endpoint);

        switch (strategy) {
            case ALLOW_ALL:
                logger.warn("DEGRADATION: Allowing all requests for {}", endpoint);
                return new RateLimitService.RateLimitResult(true, -1, "ALLOW_ALL", clientId, endpoint);

            case DENY_ALL:
                logger.warn("DEGRADATION: Denying all requests for {}", endpoint);
                return new RateLimitService.RateLimitResult(false, 0, "DENY_ALL", clientId, endpoint);

            case LIMITED_ALLOW:
                // Simple rate limiting: allow 1 in every 10 requests
                boolean allowed = (System.currentTimeMillis() % 10) == 0;
                logger.warn("DEGRADATION: Limited allow for {} - {}", endpoint, allowed ? "ALLOWED" : "DENIED");
                return new RateLimitService.RateLimitResult(allowed, allowed ? 9 : 0, "LIMITED", clientId, endpoint);

            default:
                return new RateLimitService.RateLimitResult(true, -1, "EMERGENCY", clientId, endpoint);
        }
    }

    private DegradationStrategy determineDegradationStrategy(Throwable e, String endpoint) {
        // Critical endpoints get fail-secure approach
        if (endpoint.contains("admin") || endpoint.contains("secure")) {
            return DegradationStrategy.DENY_ALL;
        }

        // Memory issues - be more restrictive
        if (e instanceof OutOfMemoryError || e.getMessage().contains("memory")) {
            return DegradationStrategy.LIMITED_ALLOW;
        }

        // Configuration issues - fail safe
        if (e.getMessage().contains("config") || e.getMessage().contains("property")) {
            return DegradationStrategy.ALLOW_ALL;
        }

        // Default: Allow with warning
        return DegradationStrategy.ALLOW_ALL;
    }

    private String extractFallbackClientId(HttpServletRequest request) {
        try {
            return "FALLBACK_" + request.getRemoteAddr();
        } catch (Exception e) {
            return "UNKNOWN_" + System.currentTimeMillis();
        }
    }
}
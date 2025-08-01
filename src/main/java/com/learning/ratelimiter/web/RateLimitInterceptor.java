// FILE PATH: src/main/java/com/learning/ratelimiter/web/RateLimitInterceptor.java

package com.learning.ratelimiter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.learning.ratelimiter.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // Skip rate limiting for certain paths (health checks, actuator endpoints)
        String requestURI = request.getRequestURI();
        if (shouldSkipRateLimiting(requestURI)) {
            logger.debug("Skipping rate limiting for path: {}", requestURI);
            return true;
        }

        try {
            // Check rate limit
            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(request);

            // Add rate limiting headers to response (regardless of outcome)
            addRateLimitHeaders(response, result, request);

            if (result.isAllowed()) {
                // Request allowed - continue processing
                logger.debug("Request allowed: {}", result);
                return true;
            } else {
                // Request denied - return 429 Too Many Requests
                logger.warn("Request rate limited: {}", result);
                handleRateLimitExceeded(request, response, result);
                return false;
            }

        } catch (Exception e) {
            logger.error("Error in rate limiting interceptor", e);
            // Fail open - allow request to continue if rate limiting fails
            return true;
        }
    }

    /**
     * Check if rate limiting should be skipped for this path
     */
    private boolean shouldSkipRateLimiting(String requestURI) {
        // Skip actuator endpoints
        if (requestURI.startsWith("/actuator/")) {
            return true;
        }

        // Skip health check endpoints
        if (requestURI.equals("/health") || requestURI.equals("/ping")) {
            return true;
        }

        // Skip static resources
        if (requestURI.startsWith("/static/") ||
                requestURI.startsWith("/css/") ||
                requestURI.startsWith("/js/") ||
                requestURI.startsWith("/images/")) {
            return true;
        }

        // Skip favicon
        if (requestURI.equals("/favicon.ico")) {
            return true;
        }

        return false;
    }

    /**
     * Add standard rate limiting headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response,
                                     RateLimitService.RateLimitResult result,
                                     HttpServletRequest request) {

        // Standard rate limiting headers
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingRequests()));
        response.setHeader("X-RateLimit-Algorithm", result.getAlgorithm());
        response.setHeader("X-RateLimit-ClientId", result.getClientId());

        // Additional debugging headers (can be disabled in production)
        response.setHeader("X-RateLimit-Endpoint", result.getEndpoint());
        response.setHeader("X-RateLimit-Timestamp", LocalDateTime.now().toString());

        // CORS headers for browser requests
        response.setHeader("Access-Control-Expose-Headers",
                "X-RateLimit-Remaining,X-RateLimit-Algorithm,X-RateLimit-ClientId");
    }

    /**
     * Handle rate limit exceeded - return 429 with detailed response
     */
    private void handleRateLimitExceeded(HttpServletRequest request,
                                         HttpServletResponse response,
                                         RateLimitService.RateLimitResult result) throws Exception {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Create detailed error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", "Too many requests. Please try again later.");
        errorResponse.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("method", request.getMethod());

        // Rate limiting details
        Map<String, Object> rateLimitInfo = new HashMap<>();
        rateLimitInfo.put("algorithm", result.getAlgorithm());
        rateLimitInfo.put("clientId", result.getClientId());
        rateLimitInfo.put("remainingRequests", result.getRemainingRequests());
        rateLimitInfo.put("endpoint", result.getEndpoint());

        errorResponse.put("rateLimitInfo", rateLimitInfo);

        // Suggestions for client
        Map<String, Object> suggestions = new HashMap<>();
        suggestions.put("retryAfter", "Wait before making another request");
        suggestions.put("contact", "Contact support if you need higher limits");
        suggestions.put("documentation", "/api/docs for rate limiting information");

        errorResponse.put("suggestions", suggestions);

        // Write JSON response
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();

        logger.info("Rate limit exceeded response sent: method={}, uri={}, clientId={}, algorithm={}",
                request.getMethod(), request.getRequestURI(), result.getClientId(), result.getAlgorithm());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        // Optional: Log request completion, collect metrics, etc.
        if (ex != null) {
            logger.error("Request completed with exception: {}", ex.getMessage());
        }
    }
}
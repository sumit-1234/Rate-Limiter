package com.learning.ratelimiter.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;


@Component
@Order(1) // Execute before rate limiting interceptor
public class LoggingFilter implements Filter {
private static final Logger logger=LoggerFactory.getLogger(LoggingFilter.class);
    // MDC Keys for structured logging
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CLIENT_IP_KEY = "clientIp";
    private static final String REQUEST_URI_KEY = "requestUri";
    private static final String HTTP_METHOD_KEY = "httpMethod";
    private static final String USER_AGENT_KEY = "userAgent";
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        long startTime = System.currentTimeMillis();
        try {
            // 1. Generate correlation ID
            String correlationId = generateCorrelationId(httpRequest);
            // 2. Add request context to MDC
            addRequestContextToMDC(httpRequest, correlationId);
            // 3. Log incoming request
            logger.info("Incoming request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

            // 4. Continue filter chain
            chain.doFilter(request, response);//If you don’t call chain.doFilter(...), the request will stop at your filter — it won’t reach the controller or next filter.
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Request completed: status={}, duration={}ms",
                    httpResponse.getStatus(), duration);
        }catch(Exception e){
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Request failed after {}ms: {}", duration, e.getMessage(), e);
            throw e; // Re-throw the exception
        }finally{
// 5. Clean up MDC
            clearMDC();
        }
    }

    private void clearMDC() {
//        MDC.remove(CORRELATION_ID_KEY);
//        MDC.remove(CLIENT_IP_KEY);
//        MDC.remove(REQUEST_URI_KEY);
//        MDC.remove(HTTP_METHOD_KEY);
//        MDC.remove(USER_AGENT_KEY);
//        MDC.remove("apiKeyPrefix");
//        MDC.remove("queryParams");
// Alternative: Clear all MDC data
         MDC.clear();
    }

    private void addRequestContextToMDC(HttpServletRequest request, String correlationId) {
        // Core request identification
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(REQUEST_URI_KEY, request.getRequestURI());
        MDC.put(HTTP_METHOD_KEY, request.getMethod());
        // Client identification
        String clientIp = extractClientIp(request);
        MDC.put(CLIENT_IP_KEY, clientIp);

        // Optional: User agent for analysis
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            // Truncate long user agents
            userAgent = userAgent.length() > 100 ? userAgent.substring(0, 100) + "..." : userAgent;
            MDC.put(USER_AGENT_KEY, userAgent);
        }

        // Optional: API Key for business context (be careful with sensitive data)
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            // Only log first 8 characters for security
            String maskedApiKey = apiKey.length() > 8 ?
                    apiKey.substring(0, 8) + "***" : apiKey + "***";
            MDC.put("apiKeyPrefix", maskedApiKey);
        }

        // Query parameters (if any)
        String queryString = request.getQueryString();
        if (queryString != null) {
            MDC.put("queryParams", queryString);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "X-Original-Forwarded-For",
                "Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Take first IP if comma-separated
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    private String generateCorrelationId(HttpServletRequest request) {
        // Check if correlation ID already exists in headers (from load balancer/gateway)
        String existingCorrelationId = request.getHeader("X-Correlation-ID");

        if (existingCorrelationId != null && !existingCorrelationId.trim().isEmpty()) {
            return existingCorrelationId;
        }

        // Generate new UUID-based correlation ID
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("LoggingFilter initialized - correlation IDs enabled");
    }

    @Override
    public void destroy() {
        logger.info("LoggingFilter destroyed");
    }
}

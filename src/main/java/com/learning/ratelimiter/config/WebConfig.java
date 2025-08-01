// FILE PATH: src/main/java/com/learning/ratelimiter/config/WebConfig.java

package com.learning.ratelimiter.config;

import com.learning.ratelimiter.web.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")  // Apply to all API endpoints
                .excludePathPatterns(
                        "/actuator/**",          // Exclude actuator endpoints
                        "/health",               // Exclude health check
                        "/ping",                 // Exclude ping endpoint
                        "/static/**",            // Exclude static resources
                        "/css/**",               // Exclude CSS
                        "/js/**",                // Exclude JavaScript
                        "/images/**",            // Exclude images
                        "/favicon.ico"           // Exclude favicon
                );
    }
}
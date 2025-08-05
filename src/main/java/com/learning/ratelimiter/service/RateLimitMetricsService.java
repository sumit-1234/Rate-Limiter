package com.learning.ratelimiter.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitMetricsService {
    private final MeterRegistry meterRegistry;

    // Counters for tracking events
    private final Counter totalRequestsCounter;
    private final Counter allowedRequestsCounter;
    private final Counter deniedRequestsCounter;

    // Timer for measuring performance
    private final Timer rateLimitCheckTimer;
    public RateLimitMetricsService(MeterRegistry masterRegistry){
        this.meterRegistry=masterRegistry;
        // Initialize counters
        this.totalRequestsCounter = Counter.builder("rate_limiter_requests_total")
                .description("Total number of rate limit checks")
                .register(meterRegistry);


        this.allowedRequestsCounter = Counter.builder("rate_limiter_requests_allowed")
                .description("Number of allowed requests")
                .register(meterRegistry);

        this.deniedRequestsCounter = Counter.builder("rate_limiter_requests_denied")
                .description("Number of denied requests")
                .register(meterRegistry);

        this.rateLimitCheckTimer = Timer.builder("rate_limiter_check_duration")
                .description("Time taken for rate limit checks")
                .register(meterRegistry);
    }
    // Methods to record metrics
    public void recordRequest(boolean allowed, String endpoint, String algorithm) {
        Counter.builder("rate_limiter_requests_total")
                .tag("endpoint", endpoint)
                .tag("algorithm", algorithm)
                .tag("status", allowed ? "allowed" : "denied")
                .register(meterRegistry)
                .increment();
        if (allowed) {
            Counter.builder("rate_limiter_requests_allowed")
                    .tag("endpoint", endpoint)
                    .tag("algorithm", algorithm)
                    .register(meterRegistry)
                    .increment();
        } else {
            Counter.builder("rate_limiter_requests_denied")
                    .tag("endpoint", endpoint)
                    .tag("algorithm", algorithm)
                    .register(meterRegistry)
                    .increment();
        }
    }
    public void recordClientActivity(String clientId, String endpoint) {
        Gauge.builder("rate_limiter_active_clients", clientId, id -> 1.0)  // ✅ Correct syntax
                .tag("endpoint", endpoint)
                .description("Number of active clients per endpoint")
                .register(meterRegistry);
    }
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    public Timer getRateLimitCheckTimer() {
        return rateLimitCheckTimer;
    }
}

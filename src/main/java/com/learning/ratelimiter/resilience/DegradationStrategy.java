package com.learning.ratelimiter.resilience;

public enum DegradationStrategy {
    ALLOW_ALL,          // Allow all requests (fail-safe)
    DENY_ALL,           // Deny all requests (fail-secure)
    LIMITED_ALLOW,      // Allow limited requests (balanced)
    CACHED_DECISION     // Use last known decision (if available)
}
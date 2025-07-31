package com.learning.ratelimiter.strategy;

public interface RateLimitingStrategy {
    boolean allowRequest(String clientId);
    void reset(String ClientId);
    long getRemainingRequests(String ClientId);
}

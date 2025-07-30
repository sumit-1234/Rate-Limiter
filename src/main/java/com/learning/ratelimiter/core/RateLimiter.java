package com.learning.ratelimiter.core;

public interface RateLimiter {
    boolean allowRequest(String clientId);
}
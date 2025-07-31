package com.learning.ratelimiter.strategy;

public enum RateLimitingAlgorithm {
    FIXED_WINDOW,
    SLIDING_WINDOW,
    LEAKY_BUCKET,
    TOKEN_BUCKET
}


package com.learning.ratelimiter.factory;

import com.learning.ratelimiter.core.SystemTimeProvider;
import com.learning.ratelimiter.core.TimeProvider;
import com.learning.ratelimiter.strategy.*;

public class RateLimiterFactory {
    public final RateLimitingAlgorithm algorithm;
    public final Configuration config;
    public final TimeProvider timeProvider;

    public RateLimiterFactory(RateLimitingAlgorithm algorithm,Configuration config){
        this(algorithm,config,new SystemTimeProvider());
    }
    public RateLimiterFactory(RateLimitingAlgorithm algorithm,Configuration config,TimeProvider timeProvider){
        this.algorithm=algorithm;
        this.config=config;
        this.timeProvider=timeProvider;
    }
    public RateLimitingStrategy createStrategy(){
        return switch(this.algorithm) {
            case FIXED_WINDOW -> new FixedWindowStrategy(config.maxRequests, config.timeWindow, timeProvider);
            case SLIDING_WINDOW -> new SlidingWindowStrategy(config.maxRequests, config.timeWindow, timeProvider);
            case LEAKY_BUCKET -> new LeakyBucketStrategy(config.maxRequests, config.timeWindow, timeProvider);
            case TOKEN_BUCKET -> new TokenBucketStrategy(config.maxRequests, config.timeWindow, timeProvider);
            default -> throw new IllegalStateException("Unexpected value: " + this.algorithm);
        };
    }

    public record Configuration(int maxRequests, long timeWindow) {
    }
}

package com.learning.ratelimiter.strategy;

import com.learning.ratelimiter.core.SystemTimeProvider;
import com.learning.ratelimiter.core.TimeProvider;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketStrategy implements RateLimitingStrategy {

    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;
    private final ConcurrentHashMap<String, TokenBucket> clientBuckets;
    private final long capacity;      // Max tokens bucket can hold
    private final double refillRate;
    // Tokens per millisecond
    private static class TokenBucket {
        final AtomicLong tokens;
        final AtomicLong lastRefillTime;
        final long capacity;
        final double refillRate;

        private TokenBucket(AtomicLong tokens, AtomicLong lastRefillTime, long capacity, double refillRate) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
            this.capacity = capacity;
            this.refillRate = refillRate;
        }
        @Override
        public String toString() {
            return "TokenBucket{" +
                    "tokens=" + tokens.get() +
                    ", lastRefillTime=" + lastRefillTime.get() +
                    ", capacity=" + capacity +
                    ", refillRate=" + refillRate +
                    '}';
        }
    }
    public TokenBucketStrategy(int maxRequests, long timeWindowMillis, TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider = timeProvider;

        // Initialize the missing fields:
        this.capacity = maxRequests;  // Bucket capacity = max requests
        this.refillRate = (double) maxRequests / timeWindowMillis;  // Tokens per millisecond
        this.clientBuckets = new ConcurrentHashMap<>();  // Initialize the map
    }

    public TokenBucketStrategy(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());
    }

    @Override
    public boolean allowRequest(String clientId) {
        // Use AtomicBoolean to capture the decision inside compute()
        AtomicBoolean shouldAllow = new AtomicBoolean(false);

        clientBuckets.compute(clientId,(key,tokenBucket)->{
            long currentTime = timeProvider.getCurrentTimeMillis();
            if(tokenBucket==null)
                tokenBucket=new TokenBucket(new AtomicLong(maxRequests),new AtomicLong(currentTime),this.capacity,this.refillRate);
            refillTokens(tokenBucket);
            boolean isConsumed= tryConsumeToken(tokenBucket);
            shouldAllow.set(isConsumed); // ← ADD THIS LINE!

            return tokenBucket;

        });


        boolean result = shouldAllow.get();
        System.out.println((result ? "✅ ALLOWED" : "❌ DENIED") + ": " + clientId +
                " - Current data: " + clientBuckets.get(clientId));

        return result;
    }
    private void refillTokens(TokenBucket bucket) {
        long currentTime = timeProvider.getCurrentTimeMillis();
        long lastRefill = bucket.lastRefillTime.get();
        long timeDelta = currentTime - lastRefill;

        if (timeDelta > 0) { // Only refill if time has passed
            long newTokens = (long) (timeDelta * bucket.refillRate);
            if (newTokens > 0) {
                // Don't exceed capacity
                long currentTokens = bucket.tokens.get();
                long maxNewTokens = Math.min(newTokens, bucket.capacity - currentTokens);
                if (maxNewTokens > 0) {
                    bucket.tokens.addAndGet(maxNewTokens);
                }
                bucket.lastRefillTime.set(currentTime);
            }
        }
    }
    private boolean tryConsumeToken(TokenBucket bucket) {
        long currentTokens;
        do {
            currentTokens = bucket.tokens.get();
            if (currentTokens <= 0) {
                return false; // No tokens available
            }
            // Try to decrement by 1 atomically
        } while (!bucket.tokens.compareAndSet(currentTokens, currentTokens - 1));

        return true; // Successfully consumed a token
    }
    @Override
    public void reset(String clientId) {
        clientBuckets.remove(clientId);
        System.out.println("🔄 RESET: " + clientId);
    }

    @Override
    public long getRemainingRequests(String ClientId) {
        return 0;
    }

}
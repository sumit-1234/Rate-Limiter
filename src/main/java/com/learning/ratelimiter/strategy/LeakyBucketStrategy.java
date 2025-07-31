// FILE PATH: src/main/java/com/learning/ratelimiter/strategy/LeakyBucketStrategy.java

package com.learning.ratelimiter.strategy;

import com.learning.ratelimiter.core.TimeProvider;
import com.learning.ratelimiter.core.SystemTimeProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LeakyBucketStrategy implements RateLimitingStrategy {

    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;
    private final ConcurrentHashMap<String, LeakyBucket> clientBuckets;

    public LeakyBucketStrategy(int maxRequests, long timeWindowMillis, TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider = timeProvider;
        this.clientBuckets = new ConcurrentHashMap<>();
    }

    public LeakyBucketStrategy(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());
    }

    private static class LeakyBucket {
        final AtomicLong currentVolume;    // Current "water" in bucket
        final AtomicLong lastLeakTime;     // When bucket last leaked
        final long capacity;               // Bucket size (max requests)
        final double leakRate;             // Volume leaked per millisecond

        LeakyBucket(long capacity, double leakRate, long currentTime) {
            this.currentVolume = new AtomicLong(0);  // Start empty
            this.lastLeakTime = new AtomicLong(currentTime);
            this.capacity = capacity;
            this.leakRate = leakRate;
        }

        @Override
        public String toString() {
            return "LeakyBucket{volume=" + currentVolume.get() +
                    ", capacity=" + capacity +
                    ", leakRate=" + leakRate + "}";
        }
    }

    @Override
    public boolean allowRequest(String clientId) {
        AtomicBoolean shouldAllow = new AtomicBoolean(false);

        clientBuckets.compute(clientId, (key, leakyBucket) -> {
            long currentTime = timeProvider.getCurrentTimeMillis();
            double leakRate = (double) maxRequests / timeWindowMillis; // Volume leaked per ms

            // Create new bucket if client doesn't exist
            if (leakyBucket == null) {
                leakyBucket = new LeakyBucket(maxRequests, leakRate, currentTime);
            }

            // Leak water from bucket based on time elapsed
            leakWater(leakyBucket, currentTime);

            // Try to add 1 request to bucket
            long currentVolume = leakyBucket.currentVolume.get();
            if (currentVolume < leakyBucket.capacity) {
                // Bucket has space - add request
                leakyBucket.currentVolume.incrementAndGet();
                shouldAllow.set(true);
                System.out.println("✅ ALLOWED: " + clientId + " - Added to bucket. Volume: " +
                        (currentVolume + 1) + "/" + leakyBucket.capacity);
            } else {
                // Bucket is full - deny request
                shouldAllow.set(false);
                System.out.println("❌ DENIED: " + clientId + " - Bucket overflow! Volume: " +
                        currentVolume + "/" + leakyBucket.capacity);
            }

            return leakyBucket;
        });

        return shouldAllow.get();
    }

    /**
     * Leak water from bucket based on elapsed time
     * This simulates the constant outflow rate
     */
    private void leakWater(LeakyBucket bucket, long currentTime) {
        long lastLeak = bucket.lastLeakTime.get();
        long timeDelta = currentTime - lastLeak;

        if (timeDelta > 0) {
            // Calculate how much volume should leak out
            long volumeToLeak = (long) (timeDelta * bucket.leakRate);

            if (volumeToLeak > 0) {
                // Reduce current volume (but not below 0)
                long currentVolume;
                long newVolume;
                do {
                    currentVolume = bucket.currentVolume.get();
                    newVolume = Math.max(0, currentVolume - volumeToLeak);
                } while (!bucket.currentVolume.compareAndSet(currentVolume, newVolume));

                // Update last leak time
                bucket.lastLeakTime.set(currentTime);

                if (volumeToLeak > 0) {
                    System.out.println("💧 LEAK: Reduced volume by " + (currentVolume - newVolume) +
                            ". New volume: " + newVolume);
                }
            }
        }
    }

    @Override
    public void reset(String clientId) {
        LeakyBucket removed = clientBuckets.remove(clientId);
        if (removed != null) {
            System.out.println("🔄 RESET: " + clientId + " - Bucket removed");
        }
    }

    @Override
    public long getRemainingRequests(String clientId) {
        LeakyBucket bucket = clientBuckets.get(clientId);
        if (bucket == null) {
            return maxRequests; // New client - full capacity available
        }

        // Leak water first to get current state
        long currentTime = timeProvider.getCurrentTimeMillis();
        leakWater(bucket, currentTime);

        long remaining = bucket.capacity - bucket.currentVolume.get();
        System.out.println("📊 REMAINING: " + clientId + " - " + remaining +
                " requests available (current volume: " + bucket.currentVolume.get() + ")");

        return Math.max(0, remaining);
    }

    /**
     * Get current bucket volume for debugging
     */
    public long getCurrentVolume(String clientId) {
        LeakyBucket bucket = clientBuckets.get(clientId);
        if (bucket == null) {
            return 0;
        }

        long currentTime = timeProvider.getCurrentTimeMillis();
        leakWater(bucket, currentTime);
        return bucket.currentVolume.get();
    }
}
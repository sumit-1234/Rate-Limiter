package com.learning.ratelimiter.strategy;

import com.learning.ratelimiter.core.SystemTimeProvider;
import com.learning.ratelimiter.core.TimeProvider;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlidingWindowStrategy implements RateLimitingStrategy {

    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;
    private final ConcurrentHashMap<String, Queue<Long>> clientTimestamps = new ConcurrentHashMap<>();

    public SlidingWindowStrategy(int maxRequests, long timeWindowMillis, TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider = timeProvider;
    }

    public SlidingWindowStrategy(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());
    }

    @Override
    public boolean allowRequest(String clientId) {
        // Use AtomicBoolean to capture the decision inside compute()
        AtomicBoolean shouldAllow = new AtomicBoolean(false);

        clientTimestamps.compute(clientId, (key, timestamps) -> {
            long currentTime = timeProvider.getCurrentTimeMillis();

            // Case 1: create new queue if clicnt doesnot exist
            if (timestamps == null) {
               timestamps=new ConcurrentLinkedDeque<>();
            }

            //remove all time stamps outside the sliding window
            removeExpiredTimestamps(timestamps, currentTime);

            //check if we can allow the requests
            if(timestamps.size()<maxRequests){
                timestamps.offer(currentTime);
                shouldAllow.set(true);
                System.out.println("✅ ALLOWED: " + clientId + " - Current requests in window: " + timestamps.size());

            }else{
                shouldAllow.set(false);
                System.out.println("❌ DENIED: " + clientId + " - Sliding window full: " + timestamps.size() + "/" + maxRequests);

            }
            return timestamps;

        });

        boolean result = shouldAllow.get();
        System.out.println((result ? "✅ ALLOWED" : "❌ DENIED") + ": " + clientId +
                " - Current data: " + clientTimestamps.get(clientId));

        return result;
    }

    private void removeExpiredTimestamps(Queue<Long> timestamps, long currentTime) {
        long windowstart=currentTime-timeWindowMillis;
        int removedCount=0;
        while(!timestamps.isEmpty() && timestamps.peek()<=windowstart){
            timestamps.poll();
            removedCount++;
        }
        if (removedCount > 0) {
            System.out.println("🧹 CLEANUP: Removed " + removedCount + " expired timestamps. Current window size: " + timestamps.size());
        }
    }

    @Override
    public void reset(String clientId) {
        Queue<Long> removed = clientTimestamps.remove(clientId);
        if (removed != null) {
            System.out.println("🔄 RESET: " + clientId + " - Cleared " + removed.size() + " timestamps");
        }
    }

    @Override
    public long getRemainingRequests(String clientId) {
        long currentTime = timeProvider.getCurrentTimeMillis();

        Queue<Long> timestamps = clientTimestamps.get(clientId);
        if (timestamps == null) {
            return maxRequests; // New client - full limit available
        }

        // Count valid timestamps in current sliding window
        long validTimestamps = timestamps.stream()
                .filter(timestamp -> (currentTime - timestamp) < timeWindowMillis)
                .count();

        long remaining = Math.max(0, maxRequests - validTimestamps);
        System.out.println("📊 REMAINING: " + clientId + " - " + remaining + " requests left (valid: " + validTimestamps + ")");

        return remaining;
    }
    /**
     * Get current window size for debugging/monitoring
     */
    public int getCurrentWindowSize(String clientId) {
        Queue<Long> timestamps = clientTimestamps.get(clientId);
        if (timestamps == null) {
            return 0;
        }

        long currentTime = timeProvider.getCurrentTimeMillis();
        removeExpiredTimestamps(timestamps, currentTime);
        return timestamps.size();
    }

    /**
     * Get all timestamps for debugging
     */
    public Queue<Long> getClientTimestamps(String clientId) {
        return clientTimestamps.get(clientId);
    }
//    private boolean isTimeWindowExpired(ClientData data, long currentTime) {
//        return (currentTime - data.windowStartTime) >= timeWindowMillis;
//    }
//
//    // Immutable ClientData for thread safety
//    private static class ClientData {
//        final int requestCount;
//        final long windowStartTime;
//
//        ClientData(int requestCount, long windowStartTime) {
//            this.requestCount = requestCount;
//            this.windowStartTime = windowStartTime;
//        }
//
//        @Override
//        public String toString() {
//            return "ClientData{count=" + requestCount + ", windowStart=" + windowStartTime + "}";
//        }
//    }
}
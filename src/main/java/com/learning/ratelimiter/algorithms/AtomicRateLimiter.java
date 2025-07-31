package com.learning.ratelimiter.algorithms;

import com.learning.ratelimiter.core.RateLimiter;
import com.learning.ratelimiter.core.SystemTimeProvider;
import com.learning.ratelimiter.core.TimeProvider;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicRateLimiter implements RateLimiter {

    // Thread-safe map
    private final ConcurrentHashMap<String, ClientData> clientRequestCounts = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;

    public AtomicRateLimiter(int maxRequests, long timeWindowMillis, TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider = timeProvider;
    }

    public AtomicRateLimiter(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());  // ← ADD THIS

    }


    // NO synchronized keyword needed
    @Override
    public boolean allowRequest(String clientId) {
        //New client
        //atomic imp
        ClientData clientData=clientRequestCounts.compute(clientId, (key, existingData) -> {
            System.out.println("Before: clientRequestCounts = " + clientRequestCounts.get(clientId));

            long currentTime = timeProvider.getCurrentTimeMillis();

            // If new client OR time window expired, create new window
            if (existingData == null || (currentTime-existingData.windowStartTime)>=timeWindowMillis) {
                return new ClientData(1, currentTime);
            } else {
                // Increment count in current window
                existingData.requestCount.incrementAndGet();
                return existingData;
            }
        });

        // Check if adding this request would exceed limit
        if (clientData.requestCount.get() > maxRequests) {
            System.out.println("DENIED: " + clientId + " has " + clientData.requestCount + " requests (limit: " + maxRequests + ")");
            return false; // Deny - over limit
        }


        // Print the map after processing
        System.out.println("After: clientRequestCounts = " + clientRequestCounts.get(clientId));
        System.out.println("ALLOWED: " + clientId + " now has " + (clientData.requestCount.get() + 1) + " requests");

        return true;
    }

    private static class ClientData {
        final AtomicInteger requestCount;  // Thread-safe counter
        final long windowStartTime;

        ClientData(int initialCount, long windowStartTime) {
            this.requestCount = new AtomicInteger(initialCount);
            this.windowStartTime = windowStartTime;

        }
        @Override
        public String toString() {
            return "ClientData{count=" + requestCount + ", windowStart=" + windowStartTime + "}";
        }
    }
}

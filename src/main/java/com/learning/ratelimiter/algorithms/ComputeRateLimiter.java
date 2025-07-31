package com.learning.ratelimiter.algorithms;

import com.learning.ratelimiter.core.RateLimiter;
import com.learning.ratelimiter.core.SystemTimeProvider;
import com.learning.ratelimiter.core.TimeProvider;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ComputeRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;
    private final ConcurrentHashMap<String, ClientData> clientData = new ConcurrentHashMap<>();

    public ComputeRateLimiter(int maxRequests, long timeWindowMillis, TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider = timeProvider;
    }

    public ComputeRateLimiter(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());
    }

    @Override
    public boolean allowRequest(String clientId) {
        // Use AtomicBoolean to capture the decision inside compute()
        AtomicBoolean shouldAllow = new AtomicBoolean(false);

        clientData.compute(clientId, (key, existingData) -> {
            long currentTime = timeProvider.getCurrentTimeMillis();

            // Case 1: New client or expired window - create new window
            if (existingData == null || isTimeWindowExpired(existingData, currentTime)) {
                shouldAllow.set(true); // First request in new window - always allow
                return new ClientData(1, currentTime);
            }

            // Case 2: Existing window - check if we can allow more requests
            if (existingData.requestCount < maxRequests) {
                shouldAllow.set(true); // Under limit - allow and increment
                return new ClientData(existingData.requestCount + 1, existingData.windowStartTime);
            } else {
                shouldAllow.set(false); // Over limit - deny and don't change data
                return existingData; // Return unchanged data
            }
        });

        boolean result = shouldAllow.get();
        System.out.println((result ? "✅ ALLOWED" : "❌ DENIED") + ": " + clientId +
                " - Current data: " + clientData.get(clientId));

        return result;
    }

//    @Override
//    public void reset(String clientId) {
//        clientData.remove(clientId);
//        System.out.println("🔄 RESET: " + clientId);
//    }
//
//    @Override
//    public long getRemainingRequests(String clientId) {
//        ClientData data = clientData.get(clientId);
//        if (data == null) {
//            return maxRequests;
//        }
//
//        long currentTime = timeProvider.getCurrentTimeMillis();
//        if (isTimeWindowExpired(data, currentTime)) {
//            return maxRequests; // Window expired, full limit available
//        }
//
//        return Math.max(0, maxRequests - data.requestCount);
//    }

    private boolean isTimeWindowExpired(ClientData data, long currentTime) {
        return (currentTime - data.windowStartTime) >= timeWindowMillis;
    }

    // Immutable ClientData for thread safety
    private static class ClientData {
        final int requestCount;
        final long windowStartTime;

        ClientData(int requestCount, long windowStartTime) {
            this.requestCount = requestCount;
            this.windowStartTime = windowStartTime;
        }

        @Override
        public String toString() {
            return "ClientData{count=" + requestCount + ", windowStart=" + windowStartTime + "}";
        }
    }
}
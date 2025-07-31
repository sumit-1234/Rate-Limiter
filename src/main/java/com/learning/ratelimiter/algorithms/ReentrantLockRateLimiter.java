package com.learning.ratelimiter.algorithms;

import com.learning.ratelimiter.core.RateLimiter;
import com.learning.ratelimiter.core.SystemTimeProvider;
import com.learning.ratelimiter.core.TimeProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockRateLimiter implements RateLimiter {

    private final Map<String, ClientData> clientRequestCounts = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock(); // One global lock
    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;

    public ReentrantLockRateLimiter(int maxRequests, long timeWindowMillis, TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider = timeProvider;
    }

    public ReentrantLockRateLimiter(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());  // ← ADD THIS

    }
    @Override
    public boolean allowRequest(String clientId) {
        lock.lock(); // Manually acquire lock
        try {
                // Print the map before processing
                System.out.println("Before: clientRequestCounts = " + clientRequestCounts.get(clientId));
                long currentTimeMillis=timeProvider.getCurrentTimeMillis();
                ClientData clientData=clientRequestCounts.get(clientId);
                //New client
                if(clientData==null || (currentTimeMillis-clientData.windowStartTime)>=timeWindowMillis){
                    clientRequestCounts.put(clientId, new ClientData(1,currentTimeMillis));
                    return true;
                }
                // Check if adding this request would exceed limit
                if (clientData.requestCount >= maxRequests) {
                    System.out.println("DENIED: " + clientId + " has " + clientData.requestCount + " requests (limit: " + maxRequests + ")");
                    return false; // Deny - over limit
                }
                clientData.requestCount++;
                // Allow request and increment count
                clientRequestCounts.put(clientId,clientData);

                // Print the map after processing
                System.out.println("After: clientRequestCounts = " + clientRequestCounts.get(clientId));
                System.out.println("ALLOWED: " + clientId + " now has " + (clientData.requestCount + 1) + " requests");

                return true;
        } finally {
            lock.unlock(); // MUST unlock in finally block
        }
    }

    class ClientData{
        int requestCount;
        long windowStartTime;
        ClientData(int requestCount, long windowStartTime){
            this.requestCount=requestCount;
            this.windowStartTime=windowStartTime;
        }
        @Override
        public String toString() {
            return "ClientData{count=" + requestCount + ", windowStart=" + windowStartTime + "}";
        }
    }
}

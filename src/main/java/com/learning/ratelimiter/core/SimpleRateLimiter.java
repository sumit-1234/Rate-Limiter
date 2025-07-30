package com.learning.ratelimiter.core;

import java.util.HashMap;
import java.util.Map;

public class SimpleRateLimiter implements RateLimiter{

    class ClientData{
         int requestCount;
         long windowStartTime;
         ClientData(int requestCount, long windowStartTime){
             this.requestCount=requestCount;
             this.windowStartTime=windowStartTime;
         }
    }

    private final int maxRequests;
    private final long timeWindowMillis;
    private final TimeProvider timeProvider;
    private final Map<String, ClientData> clientRequestCounts = new HashMap<>();
    // Keep old constructor for backward compatibility (your existing tests)
    public SimpleRateLimiter(int maxRequests, long timeWindowMillis) {
        this(maxRequests, timeWindowMillis, new SystemTimeProvider());  // ← ADD THIS
    }
    public SimpleRateLimiter(int maxRequests, long timeWindowMillis,TimeProvider timeProvider) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.timeProvider=timeProvider;
    }

    @Override
    public boolean allowRequest(String clientId) {
        // Print the map before processing
        System.out.println("Before: clientRequestCounts = " + clientRequestCounts);
        long currentTimeMillis=timeProvider.getCurrentTimeMillis();
        ClientData clientData=clientRequestCounts.get(clientId);
        //New client
        if(clientData==null || (currentTimeMillis-clientData.windowStartTime)>=timeWindowMillis){
            clientRequestCounts.put(clientId,new ClientData(1,currentTimeMillis));
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
        System.out.println("After: clientRequestCounts = " + clientRequestCounts);
        System.out.println("ALLOWED: " + clientId + " now has " + (clientData.requestCount + 1) + " requests");

        return true;
    }
}

package com.learning.ratelimiter.core;

public class SystemTimeProvider implements TimeProvider{
    @Override
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }
}

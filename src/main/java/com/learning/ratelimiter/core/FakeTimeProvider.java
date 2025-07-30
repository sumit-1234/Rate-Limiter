package com.learning.ratelimiter.core;

public class FakeTimeProvider implements TimeProvider{
    private long currenttime=0;
    @Override
    public long getCurrentTimeMillis() {
        return currenttime;
    }
    public void setCurrentTime(long timemillis){
        this.currenttime=timemillis;
    }
}

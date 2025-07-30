
package com.learning.ratelimiter.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RateLimiterTest {

    @Test
    void shouldAllowRequestWhenUnderLimit() {
        // RED PHASE: This test will FAIL because RateLimiter doesn't exist yet
        // But it clearly defines what we want our system to do

        // Given: A rate limiter with 5 requests per minute
        RateLimiter rateLimiter = new SimpleRateLimiter(5, 60000); // 5 req/60sec
        String clientId = "client-1";

        // When: Client makes first request
        boolean allowed = rateLimiter.allowRequest(clientId);

        // Then: Request should be allowed
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldDenyRequestWhenOverLimit(){
        RateLimiter rateLimiter= new SimpleRateLimiter(1,60000);
        rateLimiter.allowRequest("client-1");
        boolean secondclient=rateLimiter.allowRequest("client-1");
        assertThat(secondclient).isFalse();
    }
    @Test
    void shouldTrackDifferentClientsIndependently() {
        // This will test that different clients have separate limits
        RateLimiter rateLimiter = new SimpleRateLimiter(1, 60000); // 1 request per client

        // When: Different clients make requests
        boolean client1Request = rateLimiter.allowRequest("client-1");
        boolean client2Request = rateLimiter.allowRequest("client-2");

        // Then: Both should be allowed (independent limits)
        assertThat(client1Request).isTrue();
        assertThat(client2Request).isTrue();
    }
    @Test
    void shouldResetLimitAfterTimeWindow() {
        // This will expose that we're not handling time windows yet!
        FakeTimeProvider timeprovider=new FakeTimeProvider();
        RateLimiter rateLimiter = new SimpleRateLimiter(1, 1000,timeprovider); // 1 request per 1 second
        timeprovider.setCurrentTime(0);
        // When: Client makes request
        boolean firstRequest = rateLimiter.allowRequest("client-1");
        // Then: Should be allowed
        assertThat(firstRequest).isTrue();

        timeprovider.setCurrentTime(500);
        // When: Client makes another request immediately (should be denied)
        boolean secondRequest = rateLimiter.allowRequest("client-1");
        assertThat(secondRequest).isFalse();

        // When: We wait for time window to pass (simulate 1.1 seconds)
        // TODO: We need to simulate time passing somehow
        timeprovider.setCurrentTime(1100);
        // Then: Client should be allowed again
        boolean thirdRequest = rateLimiter.allowRequest("client-1");
        assertThat(thirdRequest).isTrue(); // This will FAIL - we never reset!
    }
}
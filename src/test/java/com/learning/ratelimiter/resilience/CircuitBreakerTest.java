package com.learning.ratelimiter.resilience;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.learning.ratelimiter.service.RateLimitService;
import com.learning.ratelimiter.service.ResilientRateLimitService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CircuitBreakerTest {

    @Autowired
    private ResilientRateLimitService resilientRateLimitService;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @Mock
    private RateLimitService rateLimitService; // Mock the underlying service

//    @Test
//    void shouldTestCircuitBreakerStates() {
//        System.out.println("Initial circuit state: " + circuitBreaker.getState());
//
//        // Circuit starts CLOSED
//        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
//
//        // You can manually control circuit for testing
//        circuitBreaker.transitionToOpenState();
//        System.out.println("Manually opened circuit: " + circuitBreaker.getState());
//
//        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
//
//        System.out.println("✅ Circuit Breaker states work correctly!");
//    }
    @Test
    void shouldOpenCircuitAfterFailures() throws Exception {
        System.out.println("\n=== Testing Circuit Breaker ===");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/hello");
        request.setRemoteAddr("127.0.0.1");

        // Setup: Make underlying service fail
        when(rateLimitService.checkRateLimit(any()))
                .thenThrow(new RuntimeException("Simulated failure"));

        // Phase 1: Trigger failures to open circuit
        System.out.println("Phase 1: Triggering failures...");
        for (int i = 1; i <= 6; i++) { // Need 5+ failures to open circuit
            try {
                RateLimitService.RateLimitResult result = resilientRateLimitService.checkRateLimit(request);
                System.out.println("Request " + i + ": " + (result.isAllowed() ? "ALLOWED" : "DENIED") +
                        " (Emergency fallback)");
            } catch (Exception e) {
                System.out.println("Request " + i + ": FAILED - " + e.getMessage());
            }
        }

        // Phase 2: Check circuit state
        CircuitBreaker.State state = circuitBreaker.getState();
        System.out.println("Circuit state after failures: " + state);

        // Phase 3: Circuit should be OPEN - requests bypass underlying service
        System.out.println("Phase 2: Testing with open circuit...");
        RateLimitService.RateLimitResult result = resilientRateLimitService.checkRateLimit(request);

        System.out.println("Request with open circuit: " +
                (result.isAllowed() ? "ALLOWED" : "DENIED") +
                " using algorithm: " + result.getAlgorithm());

        // Verify: Circuit should be OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(result.isAllowed()).isTrue(); // Emergency fallback allows request
        assertThat(result.getAlgorithm()).isEqualTo("EMERGENCY");

        System.out.println("✅ Circuit Breaker working correctly!");
    }
}

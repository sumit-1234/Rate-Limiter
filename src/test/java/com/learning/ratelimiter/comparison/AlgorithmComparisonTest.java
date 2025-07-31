// FILE PATH: src/test/java/com/learning/ratelimiter/comparison/AlgorithmComparisonTest.java

package com.learning.ratelimiter.comparison;

import com.learning.ratelimiter.core.FakeTimeProvider;
import com.learning.ratelimiter.factory.RateLimiterFactory;
import com.learning.ratelimiter.strategy.RateLimitingAlgorithm;
import com.learning.ratelimiter.strategy.RateLimitingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlgorithmComparisonTest {

    private static final int LIMIT = 10; // 10 requests
    private static final long WINDOW = 10000; // 10 seconds

    @Test
    @DisplayName("All algorithms should handle normal load correctly")
    void shouldHandleNormalLoadConsistently() {
        System.out.println("\n=== NORMAL LOAD TEST (5 requests within limit) ===");

        List<TestResult> results = new ArrayList<>();

        for (RateLimitingAlgorithm algorithm : RateLimitingAlgorithm.values()) {
            FakeTimeProvider timeProvider = new FakeTimeProvider();
            RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(LIMIT, WINDOW);
            RateLimiterFactory factory = new RateLimiterFactory(algorithm, config, timeProvider);
            RateLimitingStrategy strategy = factory.createStrategy();

            timeProvider.setCurrentTime(0);
            int allowedCount = 0;

            // Make 5 requests (well within limit)
            for (int i = 0; i < 5; i++) {
                if (strategy.allowRequest("client-1")) {
                    allowedCount++;
                }
            }

            results.add(new TestResult(algorithm.name(), allowedCount, 0));
            System.out.println(algorithm.name() + ": " + allowedCount + "/5 allowed");
        }

        // All algorithms should allow all 5 requests
        results.forEach(result ->
                assertThat(result.allowed).as(result.algorithm + " should allow all normal requests").isEqualTo(5)
        );
    }

    @Test
    @DisplayName("Compare burst handling at window boundary")
    void shouldCompareBurstHandlingAtBoundary() {
        System.out.println("\n=== BURST AT BOUNDARY TEST (Expose Fixed Window weakness) ===");

        List<TestResult> results = new ArrayList<>();

        for (RateLimitingAlgorithm algorithm : RateLimitingAlgorithm.values()) {
            FakeTimeProvider timeProvider = new FakeTimeProvider();
            RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(LIMIT, WINDOW);
            RateLimiterFactory factory = new RateLimiterFactory(algorithm, config, timeProvider);
            RateLimitingStrategy strategy = factory.createStrategy();

            int totalAllowed = 0;

            // Scenario: 10 requests at 9.9s, then 10 requests at 10.1s
            // Fixed Window should allow 20 total (BAD!)
            // Others should be more restrictive

            timeProvider.setCurrentTime(9900); // 9.9 seconds
            int firstBatchAllowed = 0;
            for (int i = 0; i < 10; i++) {
                if (strategy.allowRequest("client-1")) {
                    firstBatchAllowed++;
                }
            }

            timeProvider.setCurrentTime(10100); // 10.1 seconds (just past window)
            int secondBatchAllowed = 0;
            for (int i = 0; i < 10; i++) {
                if (strategy.allowRequest("client-1")) {
                    secondBatchAllowed++;
                }
            }

            totalAllowed = firstBatchAllowed + secondBatchAllowed;
            results.add(new TestResult(algorithm.name(), totalAllowed, 0));

            System.out.println(algorithm.name() + ": First batch: " + firstBatchAllowed +
                    "/10, Second batch: " + secondBatchAllowed + "/10, Total: " + totalAllowed + "/20");
        }

        // Fixed Window should allow more than others (showing its weakness)
        TestResult fixedWindow = results.stream()
                .filter(r -> r.algorithm.equals("FIXED_WINDOW"))
                .findFirst().orElseThrow();

        System.out.println("\nBoundary Burst Analysis:");
        System.out.println("Fixed Window allowed: " + fixedWindow.allowed + "/20 (may allow burst at boundary)");

        results.stream()
                .filter(r -> !r.algorithm.equals("FIXED_WINDOW"))
                .forEach(r -> System.out.println(r.algorithm + " allowed: " + r.allowed + "/20 (more controlled)"));
    }

    @Test
    @DisplayName("Compare behavior with gradual requests over time")
    void shouldCompareGradualRequestBehavior() {
        System.out.println("\n=== GRADUAL REQUESTS OVER TIME TEST ===");

        List<TestResult> results = new ArrayList<>();

        for (RateLimitingAlgorithm algorithm : RateLimitingAlgorithm.values()) {
            FakeTimeProvider timeProvider = new FakeTimeProvider();
            RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(LIMIT, WINDOW);
            RateLimiterFactory factory = new RateLimiterFactory(algorithm, config, timeProvider);
            RateLimitingStrategy strategy = factory.createStrategy();

            int totalAllowed = 0;

            // Make requests every 800ms for 20 seconds
            // This should allow steady flow for all algorithms
            for (int i = 0; i < 25; i++) {
                timeProvider.setCurrentTime(i * 800); // Every 0.8 seconds
                if (strategy.allowRequest("client-1")) {
                    totalAllowed++;
                }
            }

            results.add(new TestResult(algorithm.name(), totalAllowed, 0));
            System.out.println(algorithm.name() + ": " + totalAllowed + "/25 allowed over 20 seconds");
        }

        // All should handle gradual requests well
        results.forEach(result ->
                assertThat(result.allowed).as(result.algorithm + " should handle gradual requests").isGreaterThan(15)
        );
    }

    @Test
    @DisplayName("Compare immediate burst handling")
    void shouldCompareImmediateBurstHandling() {
        System.out.println("\n=== IMMEDIATE BURST TEST (15 requests at once) ===");

        List<TestResult> results = new ArrayList<>();

        for (RateLimitingAlgorithm algorithm : RateLimitingAlgorithm.values()) {
            FakeTimeProvider timeProvider = new FakeTimeProvider();
            RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(LIMIT, WINDOW);
            RateLimiterFactory factory = new RateLimiterFactory(algorithm, config, timeProvider);
            RateLimitingStrategy strategy = factory.createStrategy();

            timeProvider.setCurrentTime(0);
            int allowedCount = 0;
            int deniedCount = 0;

            // Burst: 15 requests immediately
            for (int i = 0; i < 15; i++) {
                if (strategy.allowRequest("client-1")) {
                    allowedCount++;
                } else {
                    deniedCount++;
                }
            }

            results.add(new TestResult(algorithm.name(), allowedCount, deniedCount));
            System.out.println(algorithm.name() + ": " + allowedCount + "/15 allowed, " + deniedCount + "/15 denied");
        }

        // All should deny some requests in immediate burst
        results.forEach(result -> {
            assertThat(result.allowed).as(result.algorithm + " should allow some requests").isGreaterThan(0);
            assertThat(result.denied).as(result.algorithm + " should deny some requests in burst").isGreaterThan(0);
            assertThat(result.allowed + result.denied).isEqualTo(15);
        });
    }

    @Test
    @DisplayName("Compare recovery after burst")
    void shouldCompareRecoveryAfterBurst() {
        System.out.println("\n=== RECOVERY AFTER BURST TEST ===");

        for (RateLimitingAlgorithm algorithm : RateLimitingAlgorithm.values()) {
            FakeTimeProvider timeProvider = new FakeTimeProvider();
            RateLimiterFactory.Configuration config = new RateLimiterFactory.Configuration(LIMIT, WINDOW);
            RateLimiterFactory factory = new RateLimiterFactory(algorithm, config, timeProvider);
            RateLimitingStrategy strategy = factory.createStrategy();

            // Phase 1: Initial burst (fill up the limit)
            timeProvider.setCurrentTime(0);
            int initialBurst = 0;
            for (int i = 0; i < 15; i++) {
                if (strategy.allowRequest("client-1")) {
                    initialBurst++;
                }
            }

            // Phase 2: Wait and try again
            timeProvider.setCurrentTime(WINDOW + 1000); // Past the window + buffer
            int afterWait = 0;
            for (int i = 0; i < 5; i++) {
                if (strategy.allowRequest("client-1")) {
                    afterWait++;
                }
            }

            System.out.println(algorithm.name() + ": Initial burst: " + initialBurst +
                    "/15, After wait: " + afterWait + "/5");

            // After waiting past the window, should allow new requests
            assertThat(afterWait).as(algorithm + " should recover after window").isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("Generate comparison summary")
    void shouldGenerateComparisonSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("                ALGORITHM COMPARISON SUMMARY");
        System.out.println("=".repeat(60));

        System.out.println("🏆 FIXED_WINDOW:");
        System.out.println("   ✅ Fastest performance");
        System.out.println("   ✅ Lowest memory usage");
        System.out.println("   ❌ Allows bursts at window boundaries");
        System.out.println("   🎯 Best for: High-performance APIs, simple use cases");

        System.out.println("\n🏆 SLIDING_WINDOW:");
        System.out.println("   ✅ Most accurate rate limiting");
        System.out.println("   ✅ No boundary burst issues");
        System.out.println("   ❌ Higher memory usage (stores timestamps)");
        System.out.println("   🎯 Best for: Precise rate limiting, security-critical APIs");

        System.out.println("\n🏆 TOKEN_BUCKET:");
        System.out.println("   ✅ Allows controlled bursts");
        System.out.println("   ✅ Good for bursty traffic patterns");
        System.out.println("   ⚡ Moderate performance");
        System.out.println("   🎯 Best for: APIs with burst allowance, user-facing services");

        System.out.println("\n🏆 LEAKY_BUCKET:");
        System.out.println("   ✅ Smoothest output rate");
        System.out.println("   ✅ Best for protecting downstream services");
        System.out.println("   ❌ No burst allowance");
        System.out.println("   🎯 Best for: Traffic shaping, protecting databases");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Choose based on your specific requirements!");
        System.out.println("=".repeat(60));
    }

    // Helper class for test results
    private static class TestResult {
        final String algorithm;
        final int allowed;
        final int denied;

        TestResult(String algorithm, int allowed, int denied) {
            this.algorithm = algorithm;
            this.allowed = allowed;
            this.denied = denied;
        }
    }
}

package com.learning.ratelimiter.edge;

// FILE PATH: src/test/java/com/learning/ratelimiter/edge/EdgeCaseTest.java

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "rate-limiter.client-id-strategy=API_KEY",
        "rate-limiter.endpoints.apihello.max-requests=3",
        "rate-limiter.endpoints.apihello.time-window-ms=2000",
        "rate-limiter.endpoints.apihello.algorithm=FIXED_WINDOW"
})
class EdgeCaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldHandleNullAndEmptyHeaders() {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Null and Empty Headers ===");

        // Test 1: No API key header at all
        ResponseEntity<String> noHeaderResponse = restTemplate.getForEntity(url, String.class);
        assertThat(noHeaderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ No header: " + noHeaderResponse.getStatusCode());

        // Test 2: Empty API key header
        HttpHeaders emptyHeaders = new HttpHeaders();
        emptyHeaders.set("X-API-Key", "");
        HttpEntity<String> emptyEntity = new HttpEntity<>(emptyHeaders);

        ResponseEntity<String> emptyHeaderResponse = restTemplate.exchange(
                url, HttpMethod.GET, emptyEntity, String.class);
        assertThat(emptyHeaderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ Empty header: " + emptyHeaderResponse.getStatusCode());

        // Test 3: Whitespace-only API key
        HttpHeaders whitespaceHeaders = new HttpHeaders();
        whitespaceHeaders.set("X-API-Key", "   ");
        HttpEntity<String> whitespaceEntity = new HttpEntity<>(whitespaceHeaders);

        ResponseEntity<String> whitespaceResponse = restTemplate.exchange(
                url, HttpMethod.GET, whitespaceEntity, String.class);
        assertThat(whitespaceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ Whitespace header: " + whitespaceResponse.getStatusCode());

        // Test 4: Null value header (simulated)
        HttpHeaders nullHeaders = new HttpHeaders();
        nullHeaders.set("X-API-Key", "null");  // String "null"
        HttpEntity<String> nullEntity = new HttpEntity<>(nullHeaders);

        ResponseEntity<String> nullResponse = restTemplate.exchange(
                url, HttpMethod.GET, nullEntity, String.class);
        assertThat(nullResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ 'null' string header: " + nullResponse.getStatusCode());

        // Test 5: Very long API key
        HttpHeaders longHeaders = new HttpHeaders();
        String longApiKey = "a".repeat(1000); // 1000 character API key
        longHeaders.set("X-API-Key", longApiKey);
        HttpEntity<String> longEntity = new HttpEntity<>(longHeaders);

        ResponseEntity<String> longResponse = restTemplate.exchange(
                url, HttpMethod.GET, longEntity, String.class);
        assertThat(longResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ Long API key (1000 chars): " + longResponse.getStatusCode());

        System.out.println("All header edge cases handled gracefully! 🎯");
    }

    @Test
    void shouldHandleTimeWindowReset() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Time Window Reset ===");

        // Use specific API key for this test
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "reset-test-client");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Phase 1: Exhaust the limit (3 requests)
        System.out.println("Phase 1: Exhausting rate limit...");
        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
            System.out.println("Request " + i + ": Status=" + response.getStatusCode() +
                    ", Remaining=" + remaining);
        }

        // Phase 2: Verify limit is exceeded
        System.out.println("Phase 2: Verifying rate limit exceeded...");
        ResponseEntity<String> blockedResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        assertThat(blockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        System.out.println("Request 4: " + blockedResponse.getStatusCode() + " ✅ BLOCKED as expected");

        // Phase 3: Wait for window to reset (2 seconds + buffer)
        System.out.println("Phase 3: Waiting for time window reset (2.5 seconds)...");
        Thread.sleep(2500);

        // Phase 4: Verify limit has reset
        System.out.println("Phase 4: Testing after window reset...");
        ResponseEntity<String> afterResetResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        assertThat(afterResetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String remainingAfterReset = afterResetResponse.getHeaders().getFirst("X-RateLimit-Remaining");
        assertThat(remainingAfterReset).isEqualTo("2"); // Should be 2 remaining after first request

        System.out.println("After reset: Status=" + afterResetResponse.getStatusCode() +
                ", Remaining=" + remainingAfterReset + " ✅ RESET SUCCESSFUL");
    }

    @Test
    void shouldHandleInvalidEndpoints() {
        System.out.println("\n=== Testing Invalid Endpoints ===");

        // Test 1: Endpoint not in configuration - should use defaults
        String unmappedUrl = "http://localhost:" + port + "/api/unmapped";
        ResponseEntity<String> unmappedResponse = restTemplate.getForEntity(unmappedUrl, String.class);
        assertThat(unmappedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND); // Spring Boot 404
        System.out.println("✅ Unmapped endpoint: " + unmappedResponse.getStatusCode());

        // Test 2: Deep nested path
        String nestedUrl = "http://localhost:" + port + "/api/v1/users/profile/settings";
        ResponseEntity<String> nestedResponse = restTemplate.getForEntity(nestedUrl, String.class);
        assertThat(nestedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND); // Spring Boot 404
        System.out.println("✅ Nested path: " + nestedResponse.getStatusCode());

        // Test 3: Special characters in path
        String specialUrl = "http://localhost:" + port + "/api/hello%20world";
        ResponseEntity<String> specialResponse = restTemplate.getForEntity(specialUrl, String.class);
        assertThat(specialResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND); // Spring Boot 404
        System.out.println("✅ Special characters: " + specialResponse.getStatusCode());

        // Test 4: Very long URL
        String longPath = "/api/" + "long".repeat(100);
        String longUrl = "http://localhost:" + port + longPath;
        ResponseEntity<String> longResponse = restTemplate.getForEntity(longUrl, String.class);
        assertThat(longResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND); // Spring Boot 404
        System.out.println("✅ Long URL (400+ chars): " + longResponse.getStatusCode());

        System.out.println("All invalid endpoints handled gracefully! 🎯");
    }

    @Test
    void shouldHandleConcurrentTimeWindowResets() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Concurrent Time Window Resets ===");

        // Use same API key for all requests (same client)
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "concurrent-reset-test");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Phase 1: Exhaust limit quickly
        System.out.println("Phase 1: Exhausting limit with single client...");
        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Verify limit is exceeded
        ResponseEntity<String> blockedResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        assertThat(blockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        System.out.println("✅ Limit exhausted");

        // Phase 2: Wait almost until reset time
        System.out.println("Phase 2: Waiting near reset boundary...");
        Thread.sleep(1800); // Wait 1.8 seconds (window is 2 seconds)

        // Phase 3: Concurrent requests right at reset boundary
        System.out.println("Phase 3: Sending concurrent requests at reset boundary...");
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger allowedRequests = new AtomicInteger(0);
        AtomicInteger deniedRequests = new AtomicInteger(0);

        // All threads will hit exactly when window resets
        for (int thread = 0; thread < numberOfThreads; thread++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(200); // Wait for reset (total wait = 1.8 + 0.2 = 2.0s)
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        allowedRequests.incrementAndGet();
                    } else {
                        deniedRequests.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify: Should allow exactly 3 requests (the limit), deny the rest
        System.out.println("Results: " + allowedRequests.get() + " allowed, " +
                deniedRequests.get() + " denied");

        assertThat(allowedRequests.get()).isEqualTo(3);
        assertThat(deniedRequests.get()).isEqualTo(7);
        System.out.println("✅ Concurrent reset handled correctly!");
    }

    @Test
    void shouldHandleExtremelyHighConcurrency() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Extreme Concurrency ===");

        int numberOfThreads = 100;  // Very high concurrency
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger allowedRequests = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadId = thread;
            executor.submit(() -> {
                try {
                    // Each thread uses different API key (different clients)
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-API-Key", "stress-client-" + threadId);
                    HttpEntity<String> entity = new HttpEntity<>(headers);

                    for (int request = 0; request < requestsPerThread; request++) {
                        try {
                            ResponseEntity<String> response = restTemplate.exchange(
                                    url, HttpMethod.GET, entity, String.class);
                            totalRequests.incrementAndGet();

                            if (response.getStatusCode() == HttpStatus.OK) {
                                allowedRequests.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            System.err.println("Request failed: " + e.getMessage());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue(); // All threads completed
        assertThat(errors.get()).isEqualTo(0); // No errors occurred

        System.out.println("Extreme concurrency results:");
        System.out.println("- Total requests: " + totalRequests.get());
        System.out.println("- Allowed requests: " + allowedRequests.get());
        System.out.println("- Errors: " + errors.get());
        System.out.println("- Expected allowed: " + (numberOfThreads * 3) + " (each client gets 3 requests)");

        // Each of 100 clients should get 3 allowed requests = 300 total
        assertThat(allowedRequests.get()).isEqualTo(numberOfThreads * 3);
        System.out.println("✅ Extreme concurrency handled perfectly!");
    }

    @Test
    void shouldHandleMaliciousHeaders() {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Malicious Headers ===");

        // Test 1: SQL injection attempt in API key
        HttpHeaders sqlHeaders = new HttpHeaders();
        sqlHeaders.set("X-API-Key", "'; DROP TABLE users; --");
        HttpEntity<String> sqlEntity = new HttpEntity<>(sqlHeaders);

        ResponseEntity<String> sqlResponse = restTemplate.exchange(url, HttpMethod.GET, sqlEntity, String.class);
        assertThat(sqlResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ SQL injection header handled safely");

        // Test 2: XSS attempt in headers
        HttpHeaders xssHeaders = new HttpHeaders();
        xssHeaders.set("X-API-Key", "<script>alert('xss')</script>");
        HttpEntity<String> xssEntity = new HttpEntity<>(xssHeaders);

        ResponseEntity<String> xssResponse = restTemplate.exchange(url, HttpMethod.GET, xssEntity, String.class);
        assertThat(xssResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ XSS header handled safely");





        System.out.println("All malicious headers handled gracefully! 🛡️");
    }
    @Test
    void shouldHandleInvalidHeaderCharacters() {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Invalid Header Characters ===");

        try {
            HttpHeaders invalidHeaders = new HttpHeaders();
            invalidHeaders.set("X-API-Key", "🚀💻🎯客户端"); // This will throw exception
            HttpEntity<String> invalidEntity = new HttpEntity<>(invalidHeaders);

            restTemplate.exchange(url, HttpMethod.GET, invalidEntity, String.class);

            // Should not reach here
            assertThat(false).isTrue(); // Force failure if we get here

        } catch (IllegalArgumentException e) {
            // Expected behavior - HTTP headers reject invalid characters
            assertThat(e.getMessage()).contains("invalid header value");
            System.out.println("✅ Invalid header characters properly rejected: " + e.getMessage());
        }

        System.out.println("Invalid headers handled as expected! 🛡️");
    }
    @Test
    void shouldHandleRapidBurstThenQuiet() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Burst Traffic Pattern ===");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "burst-test-client");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Phase 1: Rapid burst (exceed limit quickly)
        System.out.println("Phase 1: Sending rapid burst...");
        for (int i = 1; i <= 10; i++) {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            HttpStatus expectedStatus = (i <= 3) ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
            assertThat(response.getStatusCode()).isEqualTo(expectedStatus);

            System.out.print((i <= 3) ? "✅" : "❌");
            Thread.sleep(50); // Small delay between requests
        }
        System.out.println(" Burst complete");

        // Phase 2: Wait a bit, then try again
        System.out.println("Phase 2: Waiting 1 second then testing...");
        Thread.sleep(1000);

        ResponseEntity<String> stillBlockedResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        assertThat(stillBlockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        System.out.println("✅ Still blocked after 1 second (window hasn't reset)");

        // Phase 3: Wait for full reset
        System.out.println("Phase 3: Waiting for full reset...");
        Thread.sleep(1200); // Total wait = 2.2 seconds > 2 second window

        ResponseEntity<String> afterFullResetResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        assertThat(afterFullResetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("✅ Allowed after full window reset");

        System.out.println("Burst traffic pattern handled correctly! 💥");
    }

    @Test
    void shouldHandleSystemClockChanges() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing System Behavior Under Time Pressure ===");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "time-test-client");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Test rapid requests at exact same millisecond (as fast as possible)
        System.out.println("Sending requests as fast as possible...");
        int allowedCount = 0;
        int deniedCount = 0;

        for (int i = 1; i <= 20; i++) {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                allowedCount++;
                System.out.print("✅");
            } else {
                deniedCount++;
                System.out.print("❌");
            }
        }

        System.out.println("\nResults: " + allowedCount + " allowed, " + deniedCount + " denied");

        // Should still respect the limit even with rapid requests
        assertThat(allowedCount).isEqualTo(3);
        assertThat(deniedCount).isEqualTo(17);

        System.out.println("✅ Rate limiting accurate even with rapid requests!");
    }

    @Test
    void shouldProvideConsistentHeaders() {
        String url = "http://localhost:" + port + "/api/hello";

        System.out.println("\n=== Testing Response Header Consistency ===");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "header-test-client");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Test each request has consistent headers
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Verify required headers are present
            String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
            String algorithm = response.getHeaders().getFirst("X-RateLimit-Algorithm");
            String clientId = response.getHeaders().getFirst("X-RateLimit-ClientId");

            assertThat(remaining).isNotNull();
            assertThat(algorithm).isNotNull();
            assertThat(clientId).isNotNull();

            // Verify header values make sense
            if (i <= 3) {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(Integer.parseInt(remaining)).isEqualTo(3 - i);
            } else {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(remaining).isEqualTo("0");
            }

            assertThat(algorithm).isEqualTo("FIXED_WINDOW");
            assertThat(clientId).startsWith("API_header-test-client");

            System.out.println("Request " + i + ": Status=" + response.getStatusCode() +
                    ", Remaining=" + remaining + ", Algorithm=" + algorithm);
        }

        System.out.println("✅ All headers consistent and accurate!");
    }
}
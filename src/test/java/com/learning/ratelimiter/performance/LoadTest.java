package com.learning.ratelimiter.performance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldHandleConcurrentRequestsAccurately() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/hello";
        // Test parameters
        int numberOfThreads = 20;
        int requestsPerThread = 10;
        int totalRequests = numberOfThreads * requestsPerThread; // 200 total
        int rateLimitAllowed = 10; // /api/hello allows 10 requests

        // Expected: 10 allowed, 190 denied

        // Use ExecutorService for concurrent testing
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        // CountDownLatch for synchronization
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        // Collect results from all threads
        AtomicInteger allowedRequests = new AtomicInteger(0);
        AtomicInteger deniedRequests = new AtomicInteger(0);
        // Verify exactly 10 allowed, 190 denied
        for (int thread = 0; thread < numberOfThreads; thread++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                        if (response.getStatusCode().equals(HttpStatus.OK)) {
                            allowedRequests.incrementAndGet();
                        } else {
                            deniedRequests.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        assertThat(allowedRequests.get()).isEqualTo(10);
        assertThat(deniedRequests.get()).isEqualTo(190);
    }

    @Test
    void shouldHandleConcurrentRequestsWithCompletableFuture() throws Exception {
        String url = "http://localhost:" + port + "/api/hello";
        List<CompletableFuture<ResponseEntity<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            CompletableFuture<ResponseEntity<String>> future = CompletableFuture.supplyAsync(() -> {
                return restTemplate.getForEntity(url, String.class);
            });
            futures.add(future);
        }

        CompletableFuture<Void> allRequests = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allRequests.get(30, TimeUnit.SECONDS);
        long allowedCount = futures.stream()
                .map(CompletableFuture::join)
                .mapToLong(response -> response.getStatusCode() == HttpStatus.OK ? 1 : 0)
                .sum();
        assertThat(allowedCount).isEqualTo(10);
    }

    @Test
    void shouldMeasureRateLimiterPerformance() throws Exception {
        String url = "http://localhost:" + port + "/api/hello";

        List<CompletableFuture<Long>> timingFutures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                long startTime = System.nanoTime();
                restTemplate.getForEntity(url, String.class);
                long endTime = System.nanoTime();
                return endTime - startTime; // Return duration in nanoseconds
            });
            timingFutures.add(future);
        }

        // Collect all timing results
        List<Long> durations = timingFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Calculate performance metrics
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println("Average response time: " + (avgDuration / 1_000_000) + " ms");
        System.out.println("Max response time: " + (maxDuration / 1_000_000) + " ms");

        // Assert performance requirements
        assertThat(avgDuration / 1_000_000).isLessThan(100); // Under 100ms average
    }
}
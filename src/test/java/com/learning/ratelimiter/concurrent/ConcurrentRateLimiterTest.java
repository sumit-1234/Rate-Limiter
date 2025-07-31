package com.learning.ratelimiter.concurrent;

import com.learning.ratelimiter.algorithms.AtomicRateLimiter;
import com.learning.ratelimiter.algorithms.ComputeRateLimiter;
import com.learning.ratelimiter.algorithms.ReentrantLockRateLimiter;
import com.learning.ratelimiter.algorithms.SynchronizedRateLimiter;
import com.learning.ratelimiter.core.RateLimiter;
import com.learning.ratelimiter.core.SimpleRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ConcurrentRateLimiterTest {
    @Test
    void shouldBeThreadSafeUnderConcurrentLoad() throws InterruptedException {
//        RateLimiter rateLimiter=new SynchronizedRateLimiter(10,60000);
//        RateLimiter rateLimiter=new AtomicRateLimiter(10,60000);
//        RateLimiter rateLimiter=new ReentrantLockRateLimiter(10,60000);
        RateLimiter rateLimiter=new ComputeRateLimiter(10,60000);

        int threadCount=5;
        int requestPerThread=4;
        int totalRequests=threadCount*requestPerThread;
        int expectedAllowed=10;
        int expectedDenied=10;

        ExecutorService executor= Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch=new CountDownLatch(threadCount);
        AtomicInteger allowedRequests=new AtomicInteger(0);
        AtomicInteger deniedRequests=new AtomicInteger(0);

        for(int i=0;i<threadCount;i++){
            executor.submit(()->{
               try{
                  for(int j=0;j<requestPerThread;j++){
                      if(rateLimiter.allowRequest("client-1")){
                          allowedRequests.incrementAndGet();
                          System.out.println("✅ Request ALLOWED by thread: " + Thread.currentThread().getName());
                      }else {
                          deniedRequests.incrementAndGet();
                          System.out.println("❌ Request DENIED by thread: " + Thread.currentThread().getName());

                      }
                  }
               }finally{
                    latch.countDown();
               }
            });
        }
        latch.await();
        executor.shutdown();
        // Debug output
        System.out.println("=== RESULTS ===");
        System.out.println("Allowed: " + allowedRequests.get());
        System.out.println("Denied: " + deniedRequests.get());
        System.out.println("Total: " + (allowedRequests.get() + deniedRequests.get()));

        // Then: Should allow exactly 10 requests, deny exactly 10
        assertThat(allowedRequests.get()).isEqualTo(expectedAllowed);
        assertThat(deniedRequests.get()).isEqualTo(expectedDenied);
        assertThat(allowedRequests.get() + deniedRequests.get()).isEqualTo(totalRequests);
    }
    public class BenchmarkResult {
        private final long totalRequests;
        private final long successfulRequests;
        private final long durationMillis;

        public BenchmarkResult(long totalRequests, long successfulRequests, long durationMillis) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.durationMillis = durationMillis;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public long getSuccessfulRequests() {
            return successfulRequests;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        @Override
        public String toString() {
            return "Total: " + totalRequests +
                    ", Successful: " + successfulRequests +
                    ", Time: " + durationMillis + " ms";
        }
    }


    @Test
    void benchmarkAllApproaches() {
        List<RateLimiter> implementations = Arrays.asList(
                new SynchronizedRateLimiter(100, 60000),
                new AtomicRateLimiter(100, 60000),
                new ReentrantLockRateLimiter(100, 60000),
                new ComputeRateLimiter(100, 60000)
        );

        List<String> summary = new ArrayList<>();

        for (RateLimiter impl : implementations) {
            BenchmarkResult result = benchmarkImplementation(impl, 10, 1000);
            String summaryLine = String.format("%-25s | Total: %4d | Success: %4d | Time: %4d ms",
                    impl.getClass().getSimpleName(),
                    result.getTotalRequests(),
                    result.getSuccessfulRequests(),
                    result.getDurationMillis()
            );
            System.out.println(summaryLine);
            summary.add(summaryLine);
        }

        System.out.println("\n=== Summary Table ===");
        summary.forEach(System.out::println);
    }

    // ✅ Define benchmarkImplementation inside same class
    private BenchmarkResult benchmarkImplementation(RateLimiter rateLimiter, int threadCount, int totalRequests) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long start = System.currentTimeMillis();

        AtomicLong successCount = new AtomicLong();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                if (rateLimiter.allowRequest("testClient")) {
                    successCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - start;
        return new BenchmarkResult(totalRequests, successCount.get(), duration);
    }
}

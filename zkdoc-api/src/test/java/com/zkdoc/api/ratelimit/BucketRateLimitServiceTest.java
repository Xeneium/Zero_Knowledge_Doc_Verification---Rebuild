package com.zkdoc.api.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BucketRateLimitServiceTest {

    private BucketRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new BucketRateLimitService();
    }

    @Test
    @DisplayName("allows requests within capacity and rejects once exhausted")
    void allowsWithinCapacityAndRejectsWhenExhausted() {
        RateLimitKey key = RateLimitKey.of(RateLimitCategory.ZKP_VERIFICATION, "user:alice");

        for (int i = 0; i < RateLimitCategory.ZKP_VERIFICATION.getCapacity(); i++) {
            RateLimitDecision decision = rateLimitService.tryAcquire(key);
            assertThat(decision.allowed()).isTrue();
        }

        // Next request exceeds capacity
        RateLimitDecision rejected = rateLimitService.tryAcquire(key);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(RateLimitCategory.ZKP_VERIFICATION.getRefillPeriodSeconds());
    }

    @Test
    @DisplayName("isolated limits across distinct keys and categories")
    void isolatedLimitsAcrossKeys() {
        RateLimitKey key1 = RateLimitKey.of(RateLimitCategory.ZKP_VERIFICATION, "user:alice");
        RateLimitKey key2 = RateLimitKey.of(RateLimitCategory.ZKP_VERIFICATION, "user:bob");

        // Exhaust Alice
        for (int i = 0; i < RateLimitCategory.ZKP_VERIFICATION.getCapacity(); i++) {
            rateLimitService.tryAcquire(key1);
        }
        assertThat(rateLimitService.tryAcquire(key1).allowed()).isFalse();

        // Bob should still be permitted
        assertThat(rateLimitService.tryAcquire(key2).allowed()).isTrue();
    }

    @Test
    @DisplayName("concurrency semaphore bounded capacity enforcement")
    void concurrencySemaphoreBoundedCapacity() {
        RateLimitCategory cat = RateLimitCategory.ZKP_VERIFICATION;
        int max = cat.getMaxConcurrentRequests();

        List<Boolean> acquiredList = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            acquiredList.add(rateLimitService.tryAcquireConcurrency(cat));
        }

        // All up to max must succeed
        assertThat(acquiredList).allMatch(Boolean::booleanValue);

        // Next attempt must be rejected (saturated)
        boolean excess = rateLimitService.tryAcquireConcurrency(cat);
        assertThat(excess).isFalse();

        // Release one slot
        rateLimitService.releaseConcurrency(cat);

        // Now should succeed again
        boolean reacquired = rateLimitService.tryAcquireConcurrency(cat);
        assertThat(reacquired).isTrue();
    }

    @Test
    @DisplayName("multithreaded race condition safety on token bucket")
    void multithreadedRaceConditionSafety() throws InterruptedException, ExecutionException {
        int threads = 10;
        int attemptsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "ip:10.0.0.1");

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                startLatch.await();
                for (int j = 0; j < attemptsPerThread; j++) {
                    RateLimitDecision decision = rateLimitService.tryAcquire(key);
                    if (decision.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(executor.submit(task));
        }

        startLatch.countDown();
        for (var f : futures) {
            f.get();
        }
        executor.shutdown();

        int total = threads * attemptsPerThread;
        assertThat(allowedCount.get() + rejectedCount.get()).isEqualTo(total);
        // Total allowed cannot exceed configured capacity
        assertThat(allowedCount.get()).isLessThanOrEqualTo(RateLimitCategory.PUBLIC_VERIFICATION.getCapacity());
        assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(total - RateLimitCategory.PUBLIC_VERIFICATION.getCapacity());
    }

    @Test
    @DisplayName("bounded state memory safety: enforces eviction when limit is reached")
    void boundedStateEviction() {
        for (int i = 0; i < 11_000; i++) {
            RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "test-id-" + i);
            rateLimitService.tryAcquire(key);
        }

        // Cache count must remain bounded below or around MAX_ENTRIES, preventing OOM
        assertThat(rateLimitService.getCachedBucketCount()).isLessThanOrEqualTo(BucketRateLimitService.MAX_ENTRIES);
    }
}

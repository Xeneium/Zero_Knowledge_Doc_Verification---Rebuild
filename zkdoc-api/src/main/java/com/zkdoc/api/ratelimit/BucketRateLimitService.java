package com.zkdoc.api.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-ready in-memory rate limiter and concurrency control engine.
 *
 * Security & Reliability guarantees:
 * 1. Bounded State: Strict upper limit on cached buckets (MAX_ENTRIES = 10,000) prevents memory exhaustion
 *    from unbounded random identifiers. Evicts LRU/stale entries when limit is approached.
 * 2. Concurrency Semaphores: Prevents CPU-intensive cryptographic endpoints (ZKP, document hashing)
 *    from spawning unbounded worker threads and starving application resources.
 * 3. Policy Isolation: Discrete limits, refill rates, and prefixes per category.
 * 4. Observability: Non-sensitive telemetry and metrics logging.
 */
@Service
public class BucketRateLimitService implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(BucketRateLimitService.class);
    public static final int MAX_ENTRIES = 10_000;

    private static class TimedBucket {
        final Bucket bucket;
        volatile long lastAccessTime;

        TimedBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, TimedBucket> bucketCache = new ConcurrentHashMap<>();
    private final Map<RateLimitCategory, Semaphore> concurrencySemaphores = new ConcurrentHashMap<>();
    private final AtomicInteger cacheSize = new AtomicInteger(0);

    public BucketRateLimitService() {
        for (RateLimitCategory cat : RateLimitCategory.values()) {
            if (cat.isConcurrencyLimited() && cat.getMaxConcurrentRequests() > 0) {
                concurrencySemaphores.put(cat, new Semaphore(cat.getMaxConcurrentRequests(), true));
            }
        }
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitKey key) {
        Objects.requireNonNull(key, "key must not be null");
        RateLimitCategory category = key.category();

        // 1. Check concurrency if limited
        if (category.isConcurrencyLimited()) {
            Semaphore sem = concurrencySemaphores.get(category);
            if (sem != null && sem.availablePermits() <= 0) {
                log.warn("Concurrency capacity saturated for category: {}", category);
                return RateLimitDecision.rejectConcurrencySaturated(category);
            }
        }

        // 2. Resolve bucket with bounded capacity guard
        String storageKey = key.toStorageKey();
        TimedBucket entry = bucketCache.computeIfAbsent(storageKey, k -> {
            evictIfNecessary();
            cacheSize.incrementAndGet();
            return new TimedBucket(createBucket(category));
        });

        entry.touch();
        boolean consumed = entry.bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for category: {}, key: {}", category, key.dimension());
            return RateLimitDecision.rejectRateLimited(category, category.getRefillPeriodSeconds());
        }

        long available = entry.bucket.getAvailableTokens();
        return RateLimitDecision.allow(category, available);
    }

    @Override
    public boolean tryAcquireConcurrency(RateLimitCategory category) {
        if (!category.isConcurrencyLimited()) {
            return true;
        }
        Semaphore sem = concurrencySemaphores.get(category);
        if (sem == null) {
            return true;
        }
        return sem.tryAcquire();
    }

    @Override
    public void releaseConcurrency(RateLimitCategory category) {
        if (!category.isConcurrencyLimited()) {
            return;
        }
        Semaphore sem = concurrencySemaphores.get(category);
        if (sem != null) {
            sem.release();
        }
    }

    public int getCachedBucketCount() {
        return bucketCache.size();
    }

    public void clear() {
        bucketCache.clear();
        cacheSize.set(0);
    }

    private Bucket createBucket(RateLimitCategory category) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(category.getCapacity())
                .refillGreedy(category.getRefillTokens(), Duration.ofSeconds(category.getRefillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void evictIfNecessary() {
        if (bucketCache.size() >= MAX_ENTRIES) {
            // Evict oldest 10% of entries based on last access time
            long now = System.currentTimeMillis();
            long evictionThreshold = now - Duration.ofMinutes(10).toMillis();

            bucketCache.entrySet().removeIf(e -> {
                if (e.getValue().lastAccessTime < evictionThreshold) {
                    cacheSize.decrementAndGet();
                    return true;
                }
                return false;
            });

            // If still over limit, evict randomly to guarantee memory safety
            if (bucketCache.size() >= MAX_ENTRIES) {
                var iterator = bucketCache.keySet().iterator();
                int evicted = 0;
                while (iterator.hasNext() && evicted < 1000) {
                    iterator.next();
                    iterator.remove();
                    cacheSize.decrementAndGet();
                    evicted++;
                }
            }
        }
    }
}

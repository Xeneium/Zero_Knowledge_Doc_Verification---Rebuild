package com.zkdoc.api.ratelimit;

/**
 * Service contract for abuse protection, token-bucket rate limiting, and concurrency throttling.
 */
public interface RateLimitService {

    /**
     * Evaluates rate limiting and concurrency constraints for the specified key.
     *
     * @param key the rate limiting identity and category
     * @return decision indicating whether request is allowed, remaining tokens, or retry delay
     */
    RateLimitDecision tryAcquire(RateLimitKey key);

    /**
     * Attempts to acquire execution capacity for concurrency-limited workloads.
     *
     * @param category the endpoint category
     * @return true if concurrency slot was acquired, false if saturated
     */
    boolean tryAcquireConcurrency(RateLimitCategory category);

    /**
     * Releases an acquired concurrency slot.
     *
     * @param category the endpoint category
     */
    void releaseConcurrency(RateLimitCategory category);
}

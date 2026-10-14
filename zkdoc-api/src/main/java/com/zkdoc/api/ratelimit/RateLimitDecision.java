package com.zkdoc.api.ratelimit;

import java.util.Objects;

/**
 * Result of a rate limiting decision.
 */
public record RateLimitDecision(
        boolean allowed,
        RateLimitCategory category,
        long remainingTokens,
        long retryAfterSeconds,
        String reason) {

    public static RateLimitDecision allow(RateLimitCategory category, long remainingTokens) {
        return new RateLimitDecision(true, category, remainingTokens, 0, null);
    }

    public static RateLimitDecision rejectRateLimited(RateLimitCategory category, long retryAfterSeconds) {
        return new RateLimitDecision(false, category, 0, retryAfterSeconds, "Rate limit exceeded");
    }

    public static RateLimitDecision rejectConcurrencySaturated(RateLimitCategory category) {
        return new RateLimitDecision(false, category, 0, 5, "Concurrent execution capacity saturated");
    }
}

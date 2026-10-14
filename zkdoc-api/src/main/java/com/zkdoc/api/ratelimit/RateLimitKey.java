package com.zkdoc.api.ratelimit;

import java.util.Objects;

/**
 * Strongly typed, composite key for rate-limiting decisions.
 * Enforces bounded key lengths and prevents delimiter injection attacks.
 */
public record RateLimitKey(RateLimitCategory category, String dimension) {

    public static final int MAX_DIMENSION_LENGTH = 128;

    public RateLimitKey {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");

        // Cleanse and bound dimension to prevent memory inflation / delimiter attacks
        String cleaned = dimension.trim().replace('\n', '_').replace('\r', '_').replace(':', '_');
        if (cleaned.length() > MAX_DIMENSION_LENGTH) {
            cleaned = cleaned.substring(0, MAX_DIMENSION_LENGTH);
        }
        dimension = cleaned;
    }

    public static RateLimitKey of(RateLimitCategory category, String dimension) {
        return new RateLimitKey(category, dimension != null && !dimension.isBlank() ? dimension : "unknown");
    }

    public String toStorageKey() {
        return category.getKeyPrefix() + dimension;
    }
}

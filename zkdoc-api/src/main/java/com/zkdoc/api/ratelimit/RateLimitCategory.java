package com.zkdoc.api.ratelimit;

import java.util.Objects;

/**
 * Categorical policy definitions for abuse protection and rate limiting across the platform.
 * Differentiates limits, burst allowances, refill rates, and concurrency constraints based on
 * the cost and sensitivity of each operation.
 */
public enum RateLimitCategory {

    /**
     * Public QR and credential verification.
     * High burst allowance for user scanning, low per-second refill to prevent automated scraping.
     */
    PUBLIC_VERIFICATION(60, 60, 60, 0, false, "rl:pub:"),

    /**
     * CPU-intensive ZKP predicate verification.
     * Enforces both rate limits and hard bounded concurrency.
     */
    ZKP_VERIFICATION(30, 30, 60, 8, true, "rl:zkp-v:"),

    /**
     * Heavy ZKP proof generation for holders.
     * Enforces tight concurrency and identity-based limits.
     */
    ZKP_PROOF_GENERATION(15, 15, 60, 4, true, "rl:zkp-g:"),

    /**
     * Document uploads and cryptographic ingestion.
     * Enforces per-identity limits and concurrency limits.
     */
    DOCUMENT_UPLOAD(20, 20, 60, 4, true, "rl:doc-up:"),

    /**
     * Authoritative credential issuance.
     * Enforces strict issuer-bound quotas to prevent automated issuance loops.
     */
    CREDENTIAL_ISSUANCE(30, 30, 60, 4, false, "rl:cred-iss:"),

    /**
     * Minting and management of QR verification capabilities.
     * Prevents database bloating and bearer capability flood.
     */
    QR_CAPABILITY_MANAGEMENT(30, 30, 60, 0, false, "rl:qr-cap:"),

    /**
     * Administrative operations (audit export, key rotation, system metrics).
     * Enforces strict per-admin limits and audit tracking.
     */
    ADMIN_SECURITY_OPERATION(50, 50, 60, 0, false, "rl:admin:");

    private final int capacity;
    private final int refillTokens;
    private final int refillPeriodSeconds;
    private final int maxConcurrentRequests;
    private final boolean concurrencyLimited;
    private final String keyPrefix;

    RateLimitCategory(
            int capacity,
            int refillTokens,
            int refillPeriodSeconds,
            int maxConcurrentRequests,
            boolean concurrencyLimited,
            String keyPrefix) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodSeconds = refillPeriodSeconds;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.concurrencyLimited = concurrencyLimited;
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public int getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public boolean isConcurrencyLimited() {
        return concurrencyLimited;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }
}

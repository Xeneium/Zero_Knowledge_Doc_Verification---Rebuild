package com.zkdoc.keys;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Trust resolution engine enforcing the platform's public key trust rules.
 * Enforces:
 * 1. Key exists in authoritative registry
 * 2. Purpose strictly matches the operation
 * 3. Owner strictly matches expected owner identity
 * 4. Algorithm conforms to cryptographic baseline
 * 5. Lifecycle status permits the operation
 * 6. Historical validity timestamps comply with revocation boundaries
 */
public class KeyTrustResolver {

    private final KeyRegistry keyRegistry;

    public KeyTrustResolver(KeyRegistry keyRegistry) {
        this.keyRegistry = Objects.requireNonNull(keyRegistry, "keyRegistry must not be null");
    }

    /**
     * Resolves a public key for verification, enforcing purpose, owner, and lifecycle.
     */
    public Optional<PublicKey> resolveVerificationKey(String keyId, KeyPurpose purpose, String expectedOwnerId) {
        return resolveHistoricalVerificationKey(keyId, purpose, expectedOwnerId, null);
    }

    /**
     * Resolves a public key for historical verification, taking into account artifact creation time.
     */
    public Optional<PublicKey> resolveHistoricalVerificationKey(
            String keyId, KeyPurpose purpose, String expectedOwnerId, Instant artifactTimestamp) {
        if (keyId == null || purpose == null) {
            return Optional.empty();
        }

        Optional<RegisteredKey> keyOpt = keyRegistry.findByKeyId(keyId);
        if (keyOpt.isEmpty()) {
            return Optional.empty();
        }

        RegisteredKey key = keyOpt.get();

        // 1. Enforce purpose separation
        if (key.purpose() != purpose) {
            return Optional.empty();
        }

        // 2. Enforce owner match if specified
        if (expectedOwnerId != null && !expectedOwnerId.isBlank() && !key.ownerId().equals(expectedOwnerId)) {
            return Optional.empty();
        }

        // 3. Enforce lifecycle status
        if (key.status() == KeyStatus.REVOKED) {
            // Revoked keys fail verification closed
            return Optional.empty();
        }

        if (key.status() == KeyStatus.RETIRED) {
            // Retired keys verify historical artifacts, but cannot verify if artifact was created after revocation
            if (key.revokedAt() != null && artifactTimestamp != null && artifactTimestamp.isAfter(key.revokedAt())) {
                return Optional.empty();
            }
        }

        // 4. Validate algorithm conformance
        try {
            KeyAlgorithmPolicy.validateAlgorithm(key.algorithm(), key.algorithmParameters());
            PublicKey publicKey = key.toPublicKey();
            KeyAlgorithmPolicy.validatePublicKey(publicKey);
            return Optional.of(publicKey);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves key metadata for an active signing operation.
     */
    public Optional<RegisteredKey> resolveSigningKeyMetadata(String keyId, KeyPurpose purpose) {
        return resolveSigningKeyMetadata(keyId, purpose, null);
    }

    /**
     * Resolves key metadata for an active signing operation with owner check.
     */
    public Optional<RegisteredKey> resolveSigningKeyMetadata(String keyId, KeyPurpose purpose, String expectedOwnerId) {
        if (keyId == null || purpose == null) {
            return Optional.empty();
        }
        return keyRegistry.findByKeyId(keyId)
                .filter(k -> k.purpose() == purpose)
                .filter(k -> k.status().canSign())
                .filter(k -> expectedOwnerId == null || expectedOwnerId.isBlank() || k.ownerId().equals(expectedOwnerId));
    }
}

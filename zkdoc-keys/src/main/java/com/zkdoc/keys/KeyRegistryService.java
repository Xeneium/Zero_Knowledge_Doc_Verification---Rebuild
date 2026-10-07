package com.zkdoc.keys;

import com.zkdoc.core.crypto.RsaPssSignatureService;
import com.zkdoc.core.crypto.SignatureService;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-level orchestration service for cryptographic key management,
 * trust resolution, private-key custody signing, and historical verification.
 */
@Service
public class KeyRegistryService {

    private static final Logger log = LoggerFactory.getLogger(KeyRegistryService.class);

    private final KeyRegistry keyRegistry;
    private final SigningKeyProvider signingKeyProvider;
    private final SignatureService signatureService;
    private final KeyTrustResolver trustResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public KeyRegistryService(KeyRegistry keyRegistry, SigningKeyProvider signingKeyProvider) {
        this(keyRegistry, signingKeyProvider, new RsaPssSignatureService());
    }

    public KeyRegistryService(
            KeyRegistry keyRegistry,
            SigningKeyProvider signingKeyProvider,
            SignatureService signatureService) {
        this.keyRegistry = Objects.requireNonNull(keyRegistry, "keyRegistry must not be null");
        this.signingKeyProvider = Objects.requireNonNull(signingKeyProvider, "signingKeyProvider must not be null");
        this.signatureService = Objects.requireNonNull(signatureService, "signatureService must not be null");
        this.trustResolver = new KeyTrustResolver(keyRegistry);
    }

    public KeyRegistryService(KeyRegistry keyRegistry) {
        this(keyRegistry, new DevelopmentSigningKeyProvider(), new RsaPssSignatureService());
    }

    /**
     * Resolves a public key metadata record for verification by keyId and purpose.
     */
    public Optional<RegisteredKey> verificationKey(String keyId, KeyPurpose purpose) {
        return keyRegistry.findForVerification(keyId, purpose);
    }

    /**
     * Finds active key metadata for signing for the given purpose.
     */
    public Optional<RegisteredKey> activeSigningKey(KeyPurpose purpose) {
        return keyRegistry.findActiveForSigning(purpose);
    }

    /**
     * Registers a new public key in the trust registry.
     */
    public void register(RegisteredKey key) {
        Objects.requireNonNull(key, "key must not be null");
        KeyAlgorithmPolicy.validateAlgorithm(key.algorithm(), key.algorithmParameters());
        KeyAlgorithmPolicy.validatePublicKey(key.toPublicKey());

        keyRegistry.register(key);
        log.info("Key registered: keyId={}, purpose={}, status={}, ownerId={}",
                key.keyId(), key.purpose(), key.status(), key.ownerId());
    }

    /**
     * Transitions an existing key's status to a new status.
     * Enforces valid lifecycle state transitions.
     */
    public void updateStatus(String keyId, KeyStatus newStatus) {
        updateStatus(keyId, newStatus, null, null);
    }

    /**
     * Transitions an existing key's status with explicit reason and optional replacement key.
     */
    public void updateStatus(String keyId, KeyStatus newStatus, String revocationReason, String replacementKeyId) {
        RegisteredKey current = keyRegistry.findByKeyId(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found in registry: " + keyId));

        if (!current.status().canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Illegal lifecycle transition from " + current.status() + " to " + newStatus + " for keyId: " + keyId);
        }

        keyRegistry.updateStatus(keyId, newStatus, revocationReason, replacementKeyId);
        log.info("Key status updated: keyId={}, previousStatus={}, newStatus={}, reason={}",
                keyId, current.status(), newStatus, revocationReason);
    }

    /**
     * Rotates an active key: transitions oldKey to RETIRED and sets replacementKeyId pointer.
     */
    public void rotateKey(String oldKeyId, String newKeyId) {
        RegisteredKey oldKey = keyRegistry.findByKeyId(oldKeyId)
                .orElseThrow(() -> new IllegalArgumentException("Old key not found for rotation: " + oldKeyId));
        RegisteredKey newKey = keyRegistry.findByKeyId(newKeyId)
                .orElseThrow(() -> new IllegalArgumentException("New replacement key not found for rotation: " + newKeyId));

        if (newKey.status() != KeyStatus.ACTIVE) {
            throw new IllegalStateException("Replacement key must be in ACTIVE status: " + newKeyId);
        }
        if (oldKey.purpose() != newKey.purpose()) {
            throw new IllegalArgumentException(
                    "Key purpose mismatch during rotation: old=" + oldKey.purpose() + ", new=" + newKey.purpose());
        }

        updateStatus(oldKeyId, KeyStatus.RETIRED, "Key rotated to " + newKeyId, newKeyId);
        log.info("Key rotated successfully: oldKeyId={} -> newKeyId={}", oldKeyId, newKeyId);
    }

    /**
     * Immediately revokes a key due to compromise or administrative revocation.
     */
    public void revokeKey(String keyId, String reason) {
        updateStatus(keyId, KeyStatus.REVOKED, reason, null);
    }

    /**
     * Performs a digital signature operation under explicit keyId and purpose authorization.
     */
    public byte[] sign(String keyId, KeyPurpose purpose, byte[] digest) {
        RegisteredKey keyMeta = keyRegistry.findByKeyId(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found in registry: " + keyId));

        if (keyMeta.purpose() != purpose) {
            throw new IllegalArgumentException(
                    "Key purpose violation: Key " + keyId + " has purpose " + keyMeta.purpose() + ", requested: " + purpose);
        }
        if (!keyMeta.status().canSign()) {
            throw new IllegalStateException(
                    "Key lifecycle violation: Key " + keyId + " is in status " + keyMeta.status() + ", cannot sign");
        }

        PrivateKey privateKey = signingKeyProvider.getPrivateKey(keyId, purpose)
                .orElseThrow(() -> new IllegalStateException(
                        "Private key custody unavailable for keyId=" + keyId + ", purpose=" + purpose));

        return signatureService.signRsaPss(digest, privateKey);
    }

    /**
     * Verifies a digital signature against a trusted public key resolved from the registry.
     */
    public boolean verify(String keyId, KeyPurpose purpose, String expectedOwnerId, byte[] digest, byte[] signature) {
        return verifyHistorical(keyId, purpose, expectedOwnerId, null, digest, signature);
    }

    /**
     * Verifies a digital signature against historical trust boundaries.
     */
    public boolean verifyHistorical(
            String keyId,
            KeyPurpose purpose,
            String expectedOwnerId,
            Instant artifactTimestamp,
            byte[] digest,
            byte[] signature) {
        Optional<PublicKey> publicKey = trustResolver.resolveHistoricalVerificationKey(
                keyId, purpose, expectedOwnerId, artifactTimestamp);

        if (publicKey.isEmpty()) {
            log.warn("Trust verification rejected: keyId={}, purpose={}, expectedOwnerId={}",
                    keyId, purpose, expectedOwnerId);
            return false;
        }

        return signatureService.verifyRsaPss(digest, signature, publicKey.get());
    }

    public KeyTrustResolver getTrustResolver() {
        return trustResolver;
    }

    public SigningKeyProvider getSigningKeyProvider() {
        return signingKeyProvider;
    }
}

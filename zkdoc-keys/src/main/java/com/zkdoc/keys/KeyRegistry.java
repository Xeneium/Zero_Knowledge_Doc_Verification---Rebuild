package com.zkdoc.keys;

import java.util.List;
import java.util.Optional;

/**
 * Authoritative registry interface for cryptographic trust metadata and public keys.
 */
public interface KeyRegistry {

    /**
     * Resolves key metadata by unique key identifier.
     */
    Optional<RegisteredKey> findByKeyId(String keyId);

    /**
     * Resolves a key for verification by keyId and purpose, accepting ACTIVE, RETIRING, or RETIRED keys.
     */
    Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose);

    /**
     * Resolves a key for verification by keyId, purpose, and expected owner ID.
     */
    Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose, String expectedOwnerId);

    /**
     * Finds an active key authorized for signing for the given purpose.
     */
    Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose);

    /**
     * Finds an active key authorized for signing for the given purpose and specific owner.
     */
    Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose, String ownerId);

    /**
     * Lists all keys registered in the trust registry.
     */
    default List<RegisteredKey> findAll() {
        return List.of();
    }

    /**
     * Lists all keys registered to a specific owner.
     */
    List<RegisteredKey> findAllByOwner(String ownerId);

    /**
     * Registers a new cryptographic trust record.
     */
    void register(RegisteredKey key);

    /**
     * Updates key lifecycle status with reason and optional replacement key.
     */
    void updateStatus(String keyId, KeyStatus status, String revocationReason, String replacementKeyId);

    /**
     * Rotates an existing key: transitions oldKeyId to RETIRED and links replacementKeyId.
     */
    void rotateKey(String oldKeyId, String newKeyId);

    /**
     * Backwards-compatible status update.
     */
    default void updateStatus(String keyId, KeyStatus status) {
        updateStatus(keyId, status, null, null);
    }
}

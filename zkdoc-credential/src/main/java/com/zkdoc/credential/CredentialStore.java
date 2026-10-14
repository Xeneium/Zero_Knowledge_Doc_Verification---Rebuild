package com.zkdoc.credential;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CredentialStore {
    void save(SignedCredential credential);

    Optional<StoredCredential> findById(UUID credentialId);

    default boolean updateLifecycle(
            UUID credentialId,
            CredentialStatus targetStatus,
            int expectedVersion,
            CredentialStatus previousStatus,
            Instant suspendedAt,
            String suspensionReason,
            Instant revokedAt,
            String revocationReason) {
        return false;
    }
}

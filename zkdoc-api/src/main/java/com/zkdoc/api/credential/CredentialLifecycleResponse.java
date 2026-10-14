package com.zkdoc.api.credential;

import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.StoredCredential;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response DTO conveying credential lifecycle state and audit timestamps.
 * Does not leak internal proving material, witnesses, or private keys.
 */
public record CredentialLifecycleResponse(
        UUID credentialId,
        String issuerId,
        CredentialStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant suspendedAt,
        String suspensionReason,
        Instant revokedAt,
        String revocationReason,
        int version,
        String message) {

    public static CredentialLifecycleResponse from(StoredCredential stored, String message) {
        return new CredentialLifecycleResponse(
                stored.credentialId(),
                stored.issuerId(),
                stored.status(),
                stored.issuedAt(),
                stored.expiresAt(),
                stored.suspendedAt(),
                stored.suspensionReason(),
                stored.revokedAt(),
                stored.revocationReason(),
                stored.version(),
                message);
    }
}

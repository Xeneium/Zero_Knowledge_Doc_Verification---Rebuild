package com.zkdoc.api.qr;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative verification context resolved by the server from a validated capability token.
 * Contains only the server-anchored references necessary for verifier operations.
 */
public record CapabilityVerificationContext(
        CapabilityId capabilityId,
        String capabilityToken,
        VerificationMode mode,
        UUID credentialId,
        UUID documentId,
        CapabilityStatus status,
        boolean isUsable,
        Instant expiresAt,
        String verificationEndpoint
) {
    public CapabilityVerificationContext {
        Objects.requireNonNull(capabilityId, "capabilityId must not be null");
        Objects.requireNonNull(capabilityToken, "capabilityToken must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(verificationEndpoint, "verificationEndpoint must not be null");
    }
}

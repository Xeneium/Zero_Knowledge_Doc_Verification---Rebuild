package com.zkdoc.api.qr;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative server-side entity representing a minted verification capability.
 * Contains only identifiers and lifecycle metadata; never embeds private keys,
 * proving material, raw attributes, or document contents.
 */
public record VerificationCapability(
        CapabilityId id,
        String capabilityToken,
        UUID credentialId,
        UUID documentId,
        VerificationMode mode,
        String protocolVersion,
        CapabilityStatus status,
        String createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason
) {
    public VerificationCapability {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(capabilityToken, "capabilityToken must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (credentialId == null && documentId == null) {
            throw new IllegalArgumentException("Capability must reference either a credentialId or a documentId");
        }
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsable(Instant now) {
        return status == CapabilityStatus.ACTIVE && !isExpired(now);
    }

    public VerificationCapability revoke(Instant when, String reason) {
        return new VerificationCapability(
                id,
                capabilityToken,
                credentialId,
                documentId,
                mode,
                protocolVersion,
                CapabilityStatus.REVOKED,
                createdBy,
                createdAt,
                expiresAt,
                when,
                reason
        );
    }
}

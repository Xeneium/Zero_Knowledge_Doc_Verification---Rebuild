package com.zkdoc.credential;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative, immutable domain model of a Holder Credential.
 * Represents a holder's relationship with an authenticated credential.
 * Does NOT duplicate the issuer's attribute claims; references the canonical credential ID
 * and verifies cryptographic integrity via the bound credential digest.
 */
public record HolderCredential(
        HolderCredentialId id,
        HolderId holderId,
        CredentialId credentialId,
        byte[] credentialDigest,
        HolderCredentialStatus status,
        int version,
        Instant provisionedAt,
        Instant updatedAt) {

    public HolderCredential {
        Objects.requireNonNull(id, "HolderCredentialId must not be null");
        Objects.requireNonNull(holderId, "HolderId must not be null");
        Objects.requireNonNull(credentialId, "CredentialId must not be null");
        Objects.requireNonNull(credentialDigest, "credentialDigest must not be null");
        Objects.requireNonNull(status, "HolderCredentialStatus must not be null");
        Objects.requireNonNull(provisionedAt, "provisionedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        credentialDigest = credentialDigest.clone();
    }

    public static HolderCredential create(
            HolderId holderId,
            CredentialId credentialId,
            byte[] credentialDigest) {
        Instant now = Instant.now();
        return new HolderCredential(
                HolderCredentialId.random(),
                holderId,
                credentialId,
                credentialDigest,
                HolderCredentialStatus.ACTIVE,
                1,
                now,
                now);
    }

    @Override
    public byte[] credentialDigest() {
        return credentialDigest.clone();
    }

    /**
     * Executes an operational lifecycle state transition.
     */
    public HolderCredential transitionTo(HolderCredentialStatus nextStatus) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("Illegal status transition from " + status + " to " + nextStatus);
        }
        return new HolderCredential(
                id,
                holderId,
                credentialId,
                credentialDigest,
                nextStatus,
                version + 1,
                provisionedAt,
                Instant.now());
    }

    public boolean isBelongingTo(String subject) {
        return holderId.value().equals(subject);
    }
}

package com.zkdoc.credential;

import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative domain entity representing a credential issuing authority.
 * To maintain cryptographic security boundaries, private signing keys are never
 * held within this object.
 */
public record Issuer(
        IssuerId id,
        String name,
        IssuerStatus status,
        KeyId signingKeyId,
        Instant createdAt,
        Instant updatedAt) {

    public Issuer {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(signingKeyId, "signingKeyId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Issuer name must not be blank");
        }
    }

    public static Issuer create(IssuerId id, String name, KeyId signingKeyId) {
        Instant now = Instant.now();
        return new Issuer(id, name, IssuerStatus.ACTIVE, signingKeyId, now, now);
    }

    public Issuer suspend() {
        if (status == IssuerStatus.REVOKED) {
            throw new IllegalStateException("Cannot suspend a revoked issuer");
        }
        return new Issuer(id, name, IssuerStatus.SUSPENDED, signingKeyId, createdAt, Instant.now());
    }

    public Issuer activate() {
        if (status == IssuerStatus.REVOKED) {
            throw new IllegalStateException("Cannot activate a revoked issuer");
        }
        return new Issuer(id, name, IssuerStatus.ACTIVE, signingKeyId, createdAt, Instant.now());
    }

    public Issuer revoke() {
        return new Issuer(id, name, IssuerStatus.REVOKED, signingKeyId, createdAt, Instant.now());
    }

    public boolean canIssue() {
        return status == IssuerStatus.ACTIVE;
    }
}

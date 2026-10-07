package com.zkdoc.credential;

import com.zkdoc.core.crypto.Digest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence record representing an issued credential stored in the relational database.
 * Explicitly separated from the pure in-memory domain Credential model.
 */
public record StoredCredential(
        UUID credentialId,
        Instant expiresAt,
        CredentialStatus status,
        byte[] canonicalPayload,
        byte[] digest,
        byte[] signature) {

    public StoredCredential {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(signature, "signature must not be null");

        canonicalPayload = canonicalPayload.clone();
        digest = digest.clone();
        signature = signature.clone();
    }

    @Override
    public byte[] canonicalPayload() {
        return canonicalPayload.clone();
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    public Digest digestObject() {
        return Digest.of(digest);
    }
}

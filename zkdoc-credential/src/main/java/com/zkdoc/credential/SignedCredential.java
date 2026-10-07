package com.zkdoc.credential;

import com.zkdoc.core.crypto.Digest;
import java.util.Objects;

/**
 * Immutable authenticated credential bundle combining the authoritative domain Credential,
 * its deterministic canonical binary payload, cryptographic SHA-256 digest, and RSA-PSS signature.
 */
public record SignedCredential(
        Credential credential,
        byte[] canonicalPayload,
        byte[] digest,
        byte[] signature) {

    public SignedCredential {
        Objects.requireNonNull(credential, "credential must not be null");
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

package com.zkdoc.document;

import java.util.UUID;

/**
 * Persisted document record including cryptographic digest, signature, public token,
 * operational status, and authenticated owner identity for object-level authorization.
 */
public record DocumentRecord(
        UUID id,
        byte[] content,
        byte[] digest,
        byte[] signature,
        String publicToken,
        boolean active,
        String ownerId) {

    public DocumentRecord(
            UUID id, byte[] content, byte[] digest, byte[] signature, String publicToken, boolean active) {
        this(id, content, digest, signature, publicToken, active, "system");
    }
}

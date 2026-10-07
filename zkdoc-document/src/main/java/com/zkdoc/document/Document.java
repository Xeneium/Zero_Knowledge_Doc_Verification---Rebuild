package com.zkdoc.document;

import com.zkdoc.core.crypto.Digest;
import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative domain model representing a registered document.
 * Adheres strictly to the architectural boundary where document identity,
 * metadata, and cryptographic evidence are separated from binary artifact storage.
 */
public record Document(
        DocumentId id,
        String filename,
        String contentType,
        long fileSize,
        Digest digest,
        byte[] signature,
        String keyId,
        String publicToken,
        DocumentStatus status,
        Instant createdAt) {

    public Document {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(publicToken, "publicToken must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative: " + fileSize);
        }
        signature = signature.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    public boolean isActive() {
        return status == DocumentStatus.ACTIVE;
    }
}

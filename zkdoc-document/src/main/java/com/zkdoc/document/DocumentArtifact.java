package com.zkdoc.document;

import com.zkdoc.core.crypto.Digest;
import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative domain representation of a concrete physical document artifact.
 * Decouples content metadata and storage references from document identity.
 */
public record DocumentArtifact(
        ArtifactId id,
        DocumentId documentId,
        int artifactVersion,
        String contentType,
        long fileSize,
        Digest digest,
        String storageKey,
        ArtifactStatus status,
        Instant createdAt) {

    public DocumentArtifact {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (artifactVersion <= 0) {
            throw new IllegalArgumentException("artifactVersion must be positive: " + artifactVersion);
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative: " + fileSize);
        }
    }
}

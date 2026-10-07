package com.zkdoc.document;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for document artifacts, maintaining isolation between
 * content storage metadata and core document identities.
 */
public interface ArtifactStore {
    void save(DocumentArtifact artifact);

    Optional<DocumentArtifact> findById(ArtifactId id);

    List<DocumentArtifact> findByDocumentId(DocumentId documentId);
}

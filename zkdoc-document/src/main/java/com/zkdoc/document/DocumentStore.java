package com.zkdoc.document;

import java.util.Optional;
import java.util.UUID;

public interface DocumentStore {
    void save(DocumentRecord document);

    Optional<DocumentRecord> findById(UUID id);

    Optional<DocumentRecord> findByPublicToken(String publicToken);
}

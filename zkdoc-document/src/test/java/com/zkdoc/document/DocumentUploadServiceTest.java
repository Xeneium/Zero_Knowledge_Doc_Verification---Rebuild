package com.zkdoc.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentUploadServiceTest {
    @Test
    void verifyUploadedCopyFailsWhenOneByteChanges() {
        InMemoryDocumentStore store = new InMemoryDocumentStore();
        DocumentUploadService service = new DocumentUploadService(store, new Sha256Hasher(), new RsaPssSigner());
        DocumentRecord uploaded = service.upload(new byte[] {10, 20, 30});

        VerificationResult exactUpload = service.verifyUploadedCopy(uploaded.id(), new byte[] {10, 20, 30});
        VerificationResult oneByteChanged = service.verifyUploadedCopy(uploaded.id(), new byte[] {10, 21, 30});

        assertTrue(exactUpload.verified());
        assertFalse(oneByteChanged.verified());
    }

    private static final class InMemoryDocumentStore implements DocumentStore {
        private final Map<UUID, DocumentRecord> documents = new HashMap<>();

        @Override
        public void save(DocumentRecord document) {
            documents.put(document.id(), document);
        }

        @Override
        public Optional<DocumentRecord> findById(UUID id) {
            return Optional.ofNullable(documents.get(id));
        }

        @Override
        public Optional<DocumentRecord> findByPublicToken(String publicToken) {
            return documents.values().stream()
                    .filter(document -> document.publicToken().equals(publicToken))
                    .findFirst();
        }
    }
}

package com.zkdoc.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentUploadServiceTest {
    private InMemoryDocumentStore store;
    private DocumentUploadService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryDocumentStore();
        service = new DocumentUploadService(store, new Sha256Hasher(), new RsaPssSigner());
    }

    @Test
    void verifyUploadedCopyFailsWhenOneByteChanges() {
        DocumentRecord uploaded = service.upload(new byte[] {10, 20, 30});

        VerificationResult exactUpload = service.verifyUploadedCopy(uploaded.id(), new byte[] {10, 20, 30});
        VerificationResult oneByteChanged = service.verifyUploadedCopy(uploaded.id(), new byte[] {10, 21, 30});

        assertTrue(exactUpload.verified());
        assertFalse(oneByteChanged.verified());
    }

    @Test
    void directUploadVerificationWithoutUuidMatchesByDigest() {
        byte[] docContent = "Cryptographic Agreement Document Content".getBytes();
        DocumentRecord uploaded = service.upload(docContent, "agreement.txt", "text/plain", "alice");

        // Verify with null candidateId: resolves by SHA-256 digest directly
        DocumentVerificationOutcome outcome = service.verifyUploadedDocument(docContent, null);

        assertTrue(outcome.verified());
        assertEquals(DocumentVerificationStatus.VERIFIED, outcome.status());
        assertEquals(uploaded.id(), outcome.documentId());
        assertTrue(outcome.signatureValid());
        assertTrue(outcome.documentActive());
    }

    @Test
    void directUploadVerificationWithoutUuidReturnsNotFoundForUnknownDocument() {
        byte[] unknownContent = "Some completely unknown content".getBytes();
        DocumentVerificationOutcome outcome = service.verifyUploadedDocument(unknownContent, null);

        assertFalse(outcome.verified());
        assertEquals(DocumentVerificationStatus.NOT_FOUND, outcome.status());
        assertNull(outcome.documentId());
        assertNotNull(outcome.digest());
    }

    @Test
    void directUploadVerificationWithUuidReturnsIntegrityMismatchWhenModified() {
        byte[] originalContent = "Confidential Payroll Data".getBytes();
        DocumentRecord uploaded = service.upload(originalContent, "payroll.txt", "text/plain", "hr-admin");

        byte[] modifiedContent = "Confidential Payroll Data (Tampered)".getBytes();
        DocumentVerificationOutcome outcome = service.verifyUploadedDocument(modifiedContent, uploaded.id());

        assertFalse(outcome.verified());
        assertEquals(DocumentVerificationStatus.INTEGRITY_MISMATCH, outcome.status());
        assertEquals(uploaded.id(), outcome.documentId());
        assertFalse(outcome.signatureValid());
    }

    @Test
    void directUploadVerificationWithNonexistentUuidReturnsNotFound() {
        byte[] content = "Any content".getBytes();
        UUID randomId = UUID.randomUUID();
        DocumentVerificationOutcome outcome = service.verifyUploadedDocument(content, randomId);

        assertFalse(outcome.verified());
        assertEquals(DocumentVerificationStatus.NOT_FOUND, outcome.status());
    }

    @Test
    void directUploadVerificationReturnsRevokedForInactiveDocument() {
        byte[] content = "Deactivated Contract".getBytes();
        DocumentRecord uploaded = service.upload(content, "contract.txt", "text/plain", "bob");

        // Deactivate document
        DocumentRecord inactive = new DocumentRecord(
                uploaded.id(), uploaded.content(), uploaded.digest(), uploaded.signature(),
                uploaded.publicToken(), false, uploaded.ownerId());
        store.save(inactive);

        DocumentVerificationOutcome outcome = service.verifyUploadedDocument(content, null);

        assertFalse(outcome.verified());
        assertEquals(DocumentVerificationStatus.REVOKED, outcome.status());
        assertEquals(uploaded.id(), outcome.documentId());
        assertFalse(outcome.documentActive());
    }

    @Test
    void listDocumentsForOwnerReturnsOnlyOwnerDocumentsUnlessAdmin() {
        service.upload("Doc 1".getBytes(), "doc1.txt", "text/plain", "user-1");
        service.upload("Doc 2".getBytes(), "doc2.txt", "text/plain", "user-1");
        service.upload("Doc 3".getBytes(), "doc3.txt", "text/plain", "user-2");

        List<DocumentRecord> user1Docs = service.listDocumentsForOwner("user-1", false);
        assertEquals(2, user1Docs.size());

        List<DocumentRecord> user2Docs = service.listDocumentsForOwner("user-2", false);
        assertEquals(1, user2Docs.size());

        List<DocumentRecord> allDocs = service.listDocumentsForOwner("admin", true);
        assertEquals(3, allDocs.size());
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

        @Override
        public Optional<DocumentRecord> findByDigest(byte[] digest) {
            return documents.values().stream()
                    .filter(doc -> MessageDigest.isEqual(doc.digest(), digest))
                    .findFirst();
        }

        @Override
        public List<DocumentRecord> findAllByOwnerId(String ownerId) {
            return documents.values().stream()
                    .filter(doc -> ownerId.equals(doc.ownerId()))
                    .toList();
        }

        @Override
        public List<DocumentRecord> findAll() {
            return new ArrayList<>(documents.values());
        }
    }
}

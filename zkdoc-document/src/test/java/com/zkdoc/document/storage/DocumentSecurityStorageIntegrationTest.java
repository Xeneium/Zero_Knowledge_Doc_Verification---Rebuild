package com.zkdoc.document.storage;

import com.zkdoc.document.ArtifactId;
import com.zkdoc.document.ArtifactStore;
import com.zkdoc.document.DocumentArtifact;
import com.zkdoc.document.DocumentId;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentStore;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.RsaPssSigner;
import com.zkdoc.document.Sha256Hasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Phase 10: Document Security, Format Validation & Artifact Storage Tests")
class DocumentSecurityStorageIntegrationTest {

    private final Sha256Hasher hasher = new Sha256Hasher();
    private final RsaPssSigner signer = new RsaPssSigner();
    private final InMemoryDocumentArtifactStorage storage = new InMemoryDocumentArtifactStorage();

    static class InMemoryTestDocStore implements DocumentStore {
        private final Map<UUID, DocumentRecord> records = new ConcurrentHashMap<>();

        @Override
        public void save(DocumentRecord record) {
            records.put(record.id(), record);
        }

        @Override
        public Optional<DocumentRecord> findById(UUID id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Optional<DocumentRecord> findByPublicToken(String publicToken) {
            return records.values().stream()
                    .filter(r -> r.publicToken().equals(publicToken))
                    .findFirst();
        }

        @Override
        public Optional<DocumentRecord> findByDigest(byte[] digest) {
            return records.values().stream()
                    .filter(r -> java.security.MessageDigest.isEqual(r.digest(), digest))
                    .findFirst();
        }

        @Override
        public List<DocumentRecord> findAllByOwnerId(String ownerId) {
            return records.values().stream()
                    .filter(r -> ownerId != null && ownerId.equals(r.ownerId()))
                    .toList();
        }

        @Override
        public List<DocumentRecord> findAll() {
            return new ArrayList<>(records.values());
        }
    }

    static class InMemoryTestArtifactStore implements ArtifactStore {
        private final Map<ArtifactId, DocumentArtifact> artifacts = new ConcurrentHashMap<>();

        @Override
        public void save(DocumentArtifact artifact) {
            artifacts.put(artifact.id(), artifact);
        }

        @Override
        public Optional<DocumentArtifact> findById(ArtifactId id) {
            return Optional.ofNullable(artifacts.get(id));
        }

        @Override
        public List<DocumentArtifact> findByDocumentId(DocumentId documentId) {
            List<DocumentArtifact> list = new ArrayList<>();
            for (DocumentArtifact a : artifacts.values()) {
                if (a.documentId().equals(documentId)) {
                    list.add(a);
                }
            }
            return list;
        }
    }

    @Nested
    @DisplayName("Content & Format Allowlist Validation")
    class FormatValidationTests {

        @Test
        @DisplayName("Valid PDF header (%PDF-) is accepted")
        void validPdfIsAccepted() throws IOException {
            byte[] pdfHeader = "%PDF-1.7\nTest PDF content".getBytes(StandardCharsets.US_ASCII);
            DocumentContentValidator.validateContent(new ByteArrayInputStream(pdfHeader), DocumentContentValidator.TYPE_PDF);
        }

        @Test
        @DisplayName("Valid PNG magic header (89 50 4E 47 0D 0A 1A 0A) is accepted")
        void validPngIsAccepted() throws IOException {
            byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
            DocumentContentValidator.validateContent(new ByteArrayInputStream(pngHeader), DocumentContentValidator.TYPE_PNG);
        }

        @Test
        @DisplayName("Valid JPEG magic header (FF D8 FF) is accepted")
        void validJpegIsAccepted() throws IOException {
            byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
            DocumentContentValidator.validateContent(new ByteArrayInputStream(jpegHeader), DocumentContentValidator.TYPE_JPEG);
        }

        @Test
        @DisplayName("Valid plain text / UTF-8 content is accepted")
        void validPlainTextIsAccepted() throws IOException {
            byte[] textBytes = "Salary statement for Employee 1042".getBytes(StandardCharsets.UTF_8);
            DocumentContentValidator.validateContent(new ByteArrayInputStream(textBytes), DocumentContentValidator.TYPE_TEXT);
        }

        @Test
        @DisplayName("Reject executable Windows PE binary / polyglot (MZ header)")
        void rejectsWindowsExecutableBinary() {
            byte[] exeBytes = new byte[]{'M', 'Z', 0x00, 0x01, 0x02, 0x03};
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DocumentContentValidator.validateContent(new ByteArrayInputStream(exeBytes), DocumentContentValidator.TYPE_PDF));
            assertTrue(ex.getMessage().contains("does not match PDF magic header"));
        }

        @Test
        @DisplayName("Reject ELF binary polyglot header")
        void rejectsElfBinary() {
            byte[] elfBytes = new byte[]{0x7F, 'E', 'L', 'F', 0x01, 0x02};
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DocumentContentValidator.validateContent(new ByteArrayInputStream(elfBytes), DocumentContentValidator.TYPE_TEXT));
            assertTrue(ex.getMessage().contains("Binary or executable content rejected"));
        }

        @Test
        @DisplayName("Reject spoofed MIME type when magic bytes do not match declared extension")
        void rejectsMimeTypeSpoofing() {
            byte[] fakePdfBytes = "Plain text claiming to be a PDF".getBytes(StandardCharsets.UTF_8);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DocumentContentValidator.validateContent(new ByteArrayInputStream(fakePdfBytes), DocumentContentValidator.TYPE_PDF));
            assertTrue(ex.getMessage().contains("does not match PDF magic header"));
        }

        @Test
        @DisplayName("Sanitize malicious filename with path traversal and control characters")
        void filenameSanitizationStripsPathTraversal() {
            String dirty = "../../etc/passwd\0evil.pdf";
            String clean = DocumentContentValidator.sanitizeFilename(dirty);
            assertEquals("passwdevil.pdf", clean);
        }
    }

    @Nested
    @DisplayName("Secure Streaming Storage & Integrity Verification")
    class StorageIntegrityTests {

        @Test
        @DisplayName("Store artifact, stream back, and verify SHA-256 integrity check passes")
        void storeAndVerifyIntegritySuccess() throws IOException {
            InMemoryTestDocStore docStore = new InMemoryTestDocStore();
            InMemoryTestArtifactStore artifactStore = new InMemoryTestArtifactStore();
            DocumentUploadService service = new DocumentUploadService(docStore, artifactStore, storage, hasher, signer);

            byte[] pdfContent = "%PDF-1.7\nSensitive Salary Slip with verified content\n%%EOF".getBytes(StandardCharsets.UTF_8);
            byte[] originalDigest = hasher.hash(pdfContent);

            DocumentRecord record = service.upload(
                    pdfContent,
                    "confidential_slip.pdf",
                    DocumentContentValidator.TYPE_PDF
            );

            assertNotNull(record);
            assertArrayEquals(originalDigest, record.digest());
            assertTrue(record.active());

            // Check artifact recorded in artifactStore
            List<DocumentArtifact> artifacts = artifactStore.findByDocumentId(DocumentId.of(record.id()));
            assertFalse(artifacts.isEmpty());
            DocumentArtifact artifact = artifacts.get(0);

            // Verify storage has artifact
            assertTrue(storage.exists(artifact.storageKey()));

            // Verify integrity check passes
            boolean integrity = service.verifyArtifactStorageIntegrity(artifact.storageKey(), record.digest());
            assertTrue(integrity);

            // Stream back artifact bytes and verify exact match
            Optional<InputStream> streamOpt = service.openDocumentArtifactStream(record.id());
            assertTrue(streamOpt.isPresent());
            try (InputStream in = streamOpt.get()) {
                byte[] readBack = in.readAllBytes();
                assertArrayEquals(pdfContent, readBack);
            }
        }

        @Test
        @DisplayName("Detect tampering if stored bytes are corrupted after upload")
        void detectsCorruptedArtifactStorage() throws IOException {
            InMemoryTestDocStore docStore = new InMemoryTestDocStore();
            InMemoryTestArtifactStore artifactStore = new InMemoryTestArtifactStore();
            DocumentUploadService service = new DocumentUploadService(docStore, artifactStore, storage, hasher, signer);

            byte[] pdfContent = "%PDF-1.7\nLegitimate Document\n%%EOF".getBytes(StandardCharsets.UTF_8);
            DocumentRecord record = service.upload(
                    pdfContent,
                    "audit.pdf",
                    DocumentContentValidator.TYPE_PDF
            );

            List<DocumentArtifact> artifacts = artifactStore.findByDocumentId(DocumentId.of(record.id()));
            assertFalse(artifacts.isEmpty());
            String storageKey = artifacts.get(0).storageKey();

            // Adversary tampers with the stored artifact bytes directly in storage backend
            byte[] tamperedBytes = "%PDF-1.7\nMalicious Tampered Document\n%%EOF".getBytes(StandardCharsets.UTF_8);
            storage.store(storageKey, new ByteArrayInputStream(tamperedBytes), tamperedBytes.length);

            // verifyArtifactStorageIntegrity must detect hash mismatch and return false
            boolean intact = service.verifyArtifactStorageIntegrity(storageKey, record.digest());
            assertFalse(intact, "Tampered artifact bytes must fail cryptographic integrity check against recorded SHA-256 digest");
        }

        @Test
        @DisplayName("Decoupled architecture: verification is independent of raw document bytes")
        void zkpVerificationIsIndependentOfDocumentBytes() {
            // In Phase 10 architectural boundary:
            // Public token or credential verification evaluates digest and signature,
            // never requesting document artifact streams from storage.
            InMemoryTestDocStore docStore = new InMemoryTestDocStore();
            InMemoryTestArtifactStore artifactStore = new InMemoryTestArtifactStore();
            DocumentUploadService service = new DocumentUploadService(docStore, artifactStore, storage, hasher, signer);

            byte[] pdfContent = "%PDF-1.7\nHolder Document Content\n%%EOF".getBytes(StandardCharsets.UTF_8);
            DocumentRecord record = service.upload(
                    pdfContent,
                    "statement.pdf",
                    DocumentContentValidator.TYPE_PDF
            );

            // Metadata resolution works without loading bytes:
            Optional<DocumentRecord> fetched = docStore.findById(record.id());
            assertTrue(fetched.isPresent());
            assertArrayEquals(record.digest(), fetched.get().digest());

            // Public token verification succeeds based on token & stored metadata
            var pubResult = service.verifyPublicToken(record.publicToken());
            assertTrue(pubResult.signatureValid());
            assertTrue(pubResult.documentActive());

            // Even if raw bytes are purged or archived from storage:
            List<DocumentArtifact> artifacts = artifactStore.findByDocumentId(DocumentId.of(record.id()));
            for (DocumentArtifact a : artifacts) {
                try {
                    storage.delete(a.storageKey());
                } catch (IOException ignored) {}
            }

            // Public token verification remains fully valid without needing raw artifact bytes
            var pubResultAfterPurge = service.verifyPublicToken(record.publicToken());
            assertTrue(pubResultAfterPurge.signatureValid());
            assertTrue(pubResultAfterPurge.documentActive());
        }
    }
}

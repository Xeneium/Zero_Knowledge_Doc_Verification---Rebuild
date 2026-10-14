package com.zkdoc.document;

import com.zkdoc.core.crypto.Digest;
import com.zkdoc.document.storage.DocumentArtifactStorage;
import com.zkdoc.document.storage.DocumentContentValidator;
import com.zkdoc.document.storage.InMemoryDocumentArtifactStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DocumentUploadService {
    private final DocumentStore documentStore;
    private final ArtifactStore artifactStore;
    private final DocumentArtifactStorage artifactStorage;
    private final Sha256Hasher hasher;
    private final RsaPssSigner signer;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public DocumentUploadService(
            DocumentStore documentStore,
            @Autowired(required = false) ArtifactStore artifactStore,
            @Autowired(required = false) DocumentArtifactStorage artifactStorage,
            Sha256Hasher hasher,
            RsaPssSigner signer) {
        this.documentStore = documentStore;
        this.artifactStore = artifactStore;
        this.artifactStorage = artifactStorage != null ? artifactStorage : new InMemoryDocumentArtifactStorage();
        this.hasher = hasher;
        this.signer = signer;
    }

    public DocumentUploadService(DocumentStore documentStore, Sha256Hasher hasher, RsaPssSigner signer) {
        this(documentStore, null, null, hasher, signer);
    }

    /**
     * Uploads and secures a document artifact from byte content.
     */
    public DocumentRecord upload(byte[] content) {
        return upload(content, "document.bin", DocumentContentValidator.TYPE_TEXT, "system");
    }

    /**
     * Uploads and secures a document artifact with format validation and streaming storage.
     */
    public DocumentRecord upload(byte[] content, String filename, String contentType) {
        return upload(content, filename, contentType, "system");
    }

    /**
     * Uploads and secures a document artifact with format validation, streaming storage, and owner identity.
     */
    public DocumentRecord upload(byte[] content, String filename, String contentType, String ownerId) {
        Objects.requireNonNull(content, "content must not be null");
        if (content.length == 0) {
            throw new IllegalArgumentException("Document content must not be empty");
        }
        if (content.length > DocumentContentValidator.MAX_ALLOWED_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of "
                    + DocumentContentValidator.MAX_ALLOWED_FILE_SIZE + " bytes");
        }

        String safeFilename = DocumentContentValidator.sanitizeFilename(filename);
        String resolvedContentType = (contentType != null && !contentType.isBlank())
                ? contentType.trim().toLowerCase() : DocumentContentValidator.TYPE_TEXT;
        String resolvedOwnerId = (ownerId != null && !ownerId.isBlank()) ? ownerId.trim() : "system";

        // Perform magic byte content inspection
        try {
            DocumentContentValidator.validateContent(new ByteArrayInputStream(content), resolvedContentType);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to validate document content: " + e.getMessage(), e);
        }

        byte[] storedContent = content.clone();
        byte[] digest = hasher.hash(storedContent);
        byte[] signature = signer.sign(digest);
        UUID documentId = UUID.randomUUID();
        String storageKey = "artifacts/" + documentId + "/" + UUID.randomUUID() + ".bin";

        // Persist artifact bytes into storage abstraction
        try {
            artifactStorage.store(storageKey, new ByteArrayInputStream(storedContent), storedContent.length);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist document artifact to storage", e);
        }

        // Persist artifact metadata into ArtifactStore if available
        if (artifactStore != null) {
            DocumentArtifact artifact = new DocumentArtifact(
                    ArtifactId.of(UUID.randomUUID()),
                    DocumentId.of(documentId),
                    1,
                    resolvedContentType,
                    storedContent.length,
                    Digest.of(digest),
                    storageKey,
                    ArtifactStatus.ACTIVE,
                    Instant.now()
            );
            artifactStore.save(artifact);
        }

        DocumentRecord document = new DocumentRecord(
                documentId, storedContent, digest, signature, nextPublicToken(), true, resolvedOwnerId);
        documentStore.save(document);
        return document;
    }

    /** Mode A: calculate the hash from the server's persisted bytes. */
    public VerificationResult verifyStoredCopy(UUID id) {
        DocumentRecord document = getDocument(id);
        return new VerificationResult(id, hasValidStoredDocument(document));
    }

    /** Mode B: calculate the hash only from a newly supplied upload. */
    public VerificationResult verifyUploadedCopy(UUID id, byte[] uploadedContent) {
        Objects.requireNonNull(uploadedContent, "uploadedContent must not be null");
        DocumentRecord document = getDocument(id);
        byte[] uploadedDigest = hasher.hash(uploadedContent);
        boolean digestMatches = MessageDigest.isEqual(uploadedDigest, document.digest());
        return new VerificationResult(id, digestMatches && signer.verify(document.digest(), document.signature()));
    }

    /**
     * Verifies stored artifact bytes against recorded SHA-256 digest without loading into heap array.
     */
    public boolean verifyArtifactStorageIntegrity(String storageKey, byte[] expectedDigest) throws IOException {
        Optional<InputStream> streamOpt = artifactStorage.openStream(storageKey);
        if (streamOpt.isEmpty()) {
            return false;
        }
        try (InputStream is = streamOpt.get()) {
            byte[] calculated = hasher.hash(is);
            return MessageDigest.isEqual(calculated, expectedDigest);
        }
    }

    public PublicVerificationResult verifyPublicToken(String publicToken) {
        DocumentRecord document = documentStore.findByPublicToken(publicToken)
                .orElseThrow(() -> new DocumentNotFoundException("public verification token"));
        return new PublicVerificationResult(hasValidStoredDocument(document), document.active());
    }

    public String publicVerificationToken(UUID id) {
        return getDocument(id).publicToken();
    }

    public Optional<InputStream> openDocumentArtifactStream(UUID id) throws IOException {
        DocumentRecord document = getDocument(id);
        if (artifactStore != null) {
            var artifacts = artifactStore.findByDocumentId(DocumentId.of(id));
            if (!artifacts.isEmpty()) {
                return artifactStorage.openStream(artifacts.get(0).storageKey());
            }
        }
        return Optional.of(new ByteArrayInputStream(document.content()));
    }

    private DocumentRecord getDocument(UUID id) {
        return documentStore.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private boolean hasValidStoredDocument(DocumentRecord document) {
        return MessageDigest.isEqual(hasher.hash(document.content()), document.digest())
                && signer.verify(document.digest(), document.signature());
    }

    private String nextPublicToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}

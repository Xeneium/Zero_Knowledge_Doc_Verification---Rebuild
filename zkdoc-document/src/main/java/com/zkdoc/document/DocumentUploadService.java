package com.zkdoc.document;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentUploadService {
    private final DocumentStore documentStore;
    private final Sha256Hasher hasher;
    private final RsaPssSigner signer;
    private final SecureRandom secureRandom = new SecureRandom();

    public DocumentUploadService(DocumentStore documentStore, Sha256Hasher hasher, RsaPssSigner signer) {
        this.documentStore = documentStore;
        this.hasher = hasher;
        this.signer = signer;
    }

    public DocumentRecord upload(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] storedContent = content.clone();
        byte[] digest = hasher.hash(storedContent);
        byte[] signature = signer.sign(digest);
        DocumentRecord document = new DocumentRecord(
                UUID.randomUUID(), storedContent, digest, signature, nextPublicToken(), true);
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

    public PublicVerificationResult verifyPublicToken(String publicToken) {
        DocumentRecord document = documentStore.findByPublicToken(publicToken)
                .orElseThrow(() -> new DocumentNotFoundException("public verification token"));
        return new PublicVerificationResult(hasValidStoredDocument(document), document.active());
    }

    public String publicVerificationToken(UUID id) {
        return getDocument(id).publicToken();
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

package com.zkdoc.document;

import com.zkdoc.core.crypto.Digest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentDomainTest {

    @Test
    @DisplayName("Document domain entity enforces invariants and defensive copying of signatures")
    void documentDomainModel() {
        DocumentId docId = DocumentId.random();
        byte[] rawDigest = new byte[32];
        rawDigest[0] = 0x55;
        Digest digest = Digest.of(rawDigest);

        byte[] sig = new byte[]{1, 2, 3, 4};
        Instant now = Instant.now();

        Document doc = new Document(
                docId,
                "employment-contract.pdf",
                "application/pdf",
                1024L,
                digest,
                sig,
                "key-doc-1",
                "tok_public_1234567890abcdef",
                DocumentStatus.ACTIVE,
                now
        );

        assertEquals(docId, doc.id());
        assertEquals("employment-contract.pdf", doc.filename());
        assertEquals("application/pdf", doc.contentType());
        assertEquals(1024L, doc.fileSize());
        assertEquals(digest, doc.digest());
        assertTrue(doc.isActive());

        // Defensive copying of signature check
        sig[0] = 99;
        assertNotEquals(99, doc.signature()[0]);
    }

    @Test
    @DisplayName("DocumentArtifact represents distinct physical artifact with versioning")
    void documentArtifactModel() {
        DocumentId docId = DocumentId.random();
        ArtifactId artifactId = ArtifactId.random();
        Digest digest = Digest.of(new byte[32]);
        Instant now = Instant.now();

        DocumentArtifact artifact = new DocumentArtifact(
                artifactId,
                docId,
                1,
                "application/pdf",
                2048L,
                digest,
                "s3://zkdoc-storage/contracts/doc-1/v1.pdf",
                ArtifactStatus.ACTIVE,
                now
        );

        assertEquals(artifactId, artifact.id());
        assertEquals(docId, artifact.documentId());
        assertEquals(1, artifact.artifactVersion());
        assertEquals(ArtifactStatus.ACTIVE, artifact.status());
        assertEquals("s3://zkdoc-storage/contracts/doc-1/v1.pdf", artifact.storageKey());

        assertThrows(IllegalArgumentException.class, () ->
                new DocumentArtifact(
                        artifactId, docId, 0, "application/pdf", 2048L, digest, "s3://key", ArtifactStatus.ACTIVE, now));

        assertThrows(IllegalArgumentException.class, () ->
                new DocumentArtifact(
                        artifactId, docId, 1, "application/pdf", -1L, digest, "s3://key", ArtifactStatus.ACTIVE, now));
    }
}

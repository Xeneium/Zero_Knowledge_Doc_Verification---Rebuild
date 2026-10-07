package com.zkdoc.api.domain;

import com.zkdoc.api.Application;
import com.zkdoc.core.crypto.Digest;
import com.zkdoc.credential.*;
import com.zkdoc.document.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class)
class DomainPersistenceIntegrationTest {
    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private IssuerStore issuerStore;

    @Autowired
    private ArtifactStore artifactStore;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @Test
    @DisplayName("Issuer persistence: save, retrieve, and status update operations")
    void issuerPersistenceLifecycle() {
        IssuerId issuerId = IssuerId.of("urn:zkdoc:issuer:org-alpha");
        KeyId keyId = KeyId.of("key-alpha-2026");
        Issuer issuer = Issuer.create(issuerId, "Organization Alpha", keyId);

        issuerStore.save(issuer);

        Optional<Issuer> retrieved = issuerStore.findById(issuerId);
        assertTrue(retrieved.isPresent());
        assertEquals("Organization Alpha", retrieved.get().name());
        assertEquals(IssuerStatus.ACTIVE, retrieved.get().status());
        assertEquals(keyId, retrieved.get().signingKeyId());

        issuerStore.updateStatus(issuerId, IssuerStatus.SUSPENDED);
        Optional<Issuer> updated = issuerStore.findById(issuerId);
        assertTrue(updated.isPresent());
        assertEquals(IssuerStatus.SUSPENDED, updated.get().status());
    }

    @Test
    @DisplayName("DocumentArtifact persistence: save and retrieve artifact versioning")
    void documentArtifactPersistence() {
        DocumentId docId = DocumentId.random();
        ArtifactId artifactId = ArtifactId.random();
        Digest digest = Digest.of(new byte[32]);

        DocumentArtifact artifact = new DocumentArtifact(
                artifactId,
                docId,
                1,
                "application/pdf",
                4096L,
                digest,
                "s3://zkdoc-vault/docs/doc-1/v1.pdf",
                ArtifactStatus.ACTIVE,
                Instant.now()
        );

        artifactStore.save(artifact);

        Optional<DocumentArtifact> retrieved = artifactStore.findById(artifactId);
        assertTrue(retrieved.isPresent());
        assertEquals(1, retrieved.get().artifactVersion());
        assertEquals("application/pdf", retrieved.get().contentType());
        assertEquals(4096L, retrieved.get().fileSize());
        assertEquals(digest, retrieved.get().digest());
        assertEquals("s3://zkdoc-vault/docs/doc-1/v1.pdf", retrieved.get().storageKey());

        List<DocumentArtifact> docArtifacts = artifactStore.findByDocumentId(docId);
        assertEquals(1, docArtifacts.size());
        assertEquals(artifactId, docArtifacts.get(0).id());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-domain-persist-it-").resolve("zkdoc-domain.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}

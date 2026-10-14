package com.zkdoc.document.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilesystemDocumentArtifactStorage Hardening & Integrity Tests")
class FilesystemDocumentArtifactStorageTest {

    @TempDir
    Path tempStorageDir;

    private FilesystemDocumentArtifactStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FilesystemDocumentArtifactStorage(tempStorageDir);
    }

    @Test
    @DisplayName("Successfully store, verify, open stream, and delete artifact")
    void storeAndRetrieveArtifact() throws IOException, NoSuchAlgorithmException {
        String key = "documents/2026/10/report.pdf";
        byte[] content = "%PDF-1.7 Test Document Content".getBytes(StandardCharsets.UTF_8);
        byte[] expectedHash = MessageDigest.getInstance("SHA-256").digest(content);

        long storedBytes = storage.store(key, new ByteArrayInputStream(content), content.length);
        assertEquals(content.length, storedBytes);

        assertTrue(storage.exists(key));
        assertTrue(storage.verifyIntegrity(key, expectedHash));

        Optional<InputStream> streamOpt = storage.openStream(key);
        assertTrue(streamOpt.isPresent());
        try (InputStream in = streamOpt.get()) {
            assertArrayEquals(content, in.readAllBytes());
        }

        assertTrue(storage.delete(key));
        assertFalse(storage.exists(key));
        assertFalse(storage.delete(key));
    }

    @Test
    @DisplayName("Detect tampering when stored file content is altered on disk")
    void detectTamperedArtifact() throws IOException, NoSuchAlgorithmException {
        String key = "sensitive/contract.bin";
        byte[] content = "Original Untampered Content".getBytes(StandardCharsets.UTF_8);
        byte[] originalHash = MessageDigest.getInstance("SHA-256").digest(content);

        storage.store(key, new ByteArrayInputStream(content), content.length);
        assertTrue(storage.verifyIntegrity(key, originalHash));

        // Tamper directly with the underlying file on disk
        Path physicalPath = storage.resolveSecurePath(key);
        Files.writeString(physicalPath, "Adversary Tampered Content");

        assertFalse(storage.verifyIntegrity(key, originalHash),
                "Integrity verification must fail if file content is tampered");
    }

    @Test
    @DisplayName("Prevent path traversal attempts outside storage root")
    void preventPathTraversal() {
        assertThrows(SecurityException.class, () ->
                storage.resolveSecurePath("../outside.txt"));

        assertThrows(SecurityException.class, () ->
                storage.resolveSecurePath("nested/../../../../etc/passwd"));

        assertThrows(SecurityException.class, () ->
                storage.resolveSecurePath("..\\windows\\system32\\cmd.exe"));

        assertThrows(SecurityException.class, () ->
                storage.resolveSecurePath("test/null\0byte.txt"));

        assertThrows(IllegalArgumentException.class, () ->
                storage.resolveSecurePath(""));

        assertThrows(IllegalArgumentException.class, () ->
                storage.resolveSecurePath("   "));
    }

    @Test
    @DisplayName("Handle expected size mismatch safely and clean up temporary files")
    void handleSizeMismatch() throws IOException {
        String key = "test/mismatch.bin";
        byte[] content = "Actual bytes here".getBytes(StandardCharsets.UTF_8);

        // Claim size is 100 bytes, but only provide content.length
        IOException ex = assertThrows(IOException.class, () ->
                storage.store(key, new ByteArrayInputStream(content), 100));

        assertTrue(ex.getMessage().contains("Stream size mismatch"));
        assertFalse(storage.exists(key), "Target file must not exist after failed store");

        // Verify no leftover .tmp files
        try (var stream = Files.walk(tempStorageDir)) {
            long tmpFiles = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, tmpFiles, "Temporary files must be cleaned up on failure");
        }
    }

    @Test
    @DisplayName("Non-existent artifact handling returns clean empty results")
    void nonExistentArtifactHandling() throws IOException {
        String missingKey = "does/not/exist.dat";
        assertFalse(storage.exists(missingKey));
        assertTrue(storage.openStream(missingKey).isEmpty());
        assertFalse(storage.verifyIntegrity(missingKey, new byte[32]));
    }
}

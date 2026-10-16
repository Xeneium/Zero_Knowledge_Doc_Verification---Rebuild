package com.zkdoc.document.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.zkdoc.document.storage.key.EnvironmentStorageKeyProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FIND-003 Remediation: AES-256-GCM Envelope Storage Encryption Tests")
class EncryptedDocumentArtifactStorageTest {

    @TempDir
    Path tempDir;

    private FilesystemDocumentArtifactStorage filesystemStorage;
    private EnvironmentStorageKeyProvider keyProvider;
    private EncryptedDocumentArtifactStorage encryptedStorage;

    private static final String MASTER_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"; // 256-bit hex
    private static final String SAMPLE_CONTENT = "Confidential Academic Degree Document: GPA 3.95, Student Alice Chen";

    @BeforeEach
    void setUp() {
        filesystemStorage = new FilesystemDocumentArtifactStorage(tempDir);
        keyProvider = new EnvironmentStorageKeyProvider("primary-key", MASTER_KEY);
        encryptedStorage = new EncryptedDocumentArtifactStorage(filesystemStorage, keyProvider, true);
    }

    @Test
    @DisplayName("Storing artifact encrypts at rest and decrypts back to original plaintext")
    void storeAndRetrieveEncryptedDocument() throws Exception {
        String key = "degrees/alice-degree.pdf";
        byte[] inputBytes = SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8);

        long written = encryptedStorage.store(key, new ByteArrayInputStream(inputBytes), inputBytes.length);
        assertEquals(inputBytes.length, written);

        Optional<InputStream> streamOpt = encryptedStorage.openStream(key);
        assertTrue(streamOpt.isPresent(), "Decrypted stream should be present");

        try (InputStream in = streamOpt.get()) {
            byte[] decryptedBytes = in.readAllBytes();
            assertEquals(SAMPLE_CONTENT, new String(decryptedBytes, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("Stored bytes on underlying disk start with ZKDE header and contain no plaintext leak")
    void verifyCiphertextOnDiskDoesNotContainPlaintext() throws Exception {
        String key = "test/confidential.txt";
        byte[] inputBytes = SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8);

        encryptedStorage.store(key, new ByteArrayInputStream(inputBytes), inputBytes.length);

        // Read raw disk bytes directly from underlying storage without decryption
        Optional<InputStream> rawOpt = filesystemStorage.openStream(key);
        assertTrue(rawOpt.isPresent());

        byte[] rawDiskBytes;
        try (InputStream raw = rawOpt.get()) {
            rawDiskBytes = raw.readAllBytes();
        }

        // Header check
        byte[] magic = Arrays.copyOfRange(rawDiskBytes, 0, EncryptedDocumentArtifactStorage.MAGIC_BYTES.length);
        assertArrayEquals(EncryptedDocumentArtifactStorage.MAGIC_BYTES, magic);

        // Ensure sensitive plaintext string is nowhere in the disk bytes
        String rawDiskContent = new String(rawDiskBytes, StandardCharsets.ISO_8859_1);
        assertFalse(rawDiskContent.contains("Confidential Academic Degree"));
        assertFalse(rawDiskContent.contains("Alice Chen"));
    }

    @Test
    @DisplayName("Legacy unencrypted documents open transparently for backward compatibility")
    void legacyPlaintextBackwardCompatibility() throws Exception {
        String key = "legacy/doc-v1.txt";
        byte[] legacyPlaintext = "Legacy unencrypted document from phase 1".getBytes(StandardCharsets.UTF_8);

        // Store directly via raw filesystem storage (simulating legacy files)
        filesystemStorage.store(key, new ByteArrayInputStream(legacyPlaintext), legacyPlaintext.length);

        // Open via EncryptedDocumentArtifactStorage
        Optional<InputStream> inOpt = encryptedStorage.openStream(key);
        assertTrue(inOpt.isPresent());

        try (InputStream in = inOpt.get()) {
            byte[] read = in.readAllBytes();
            assertEquals("Legacy unencrypted document from phase 1", new String(read, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("Tampered ciphertext bytes trigger SecurityException on decryption")
    void tamperedCiphertextThrowsSecurityException() throws Exception {
        String key = "tamper/payload.bin";
        byte[] inputBytes = SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8);

        encryptedStorage.store(key, new ByteArrayInputStream(inputBytes), inputBytes.length);

        // Corrupt one byte of ciphertext on disk
        Path diskPath = filesystemStorage.resolveSecurePath(key);
        byte[] diskBytes = Files.readAllBytes(diskPath);
        diskBytes[diskBytes.length - 1] ^= 0x01; // flip last byte (in GCM auth tag)
        Files.write(diskPath, diskBytes);

        // Attempting to open the tampered stream must fail closed
        SecurityException ex = assertThrows(SecurityException.class, () -> encryptedStorage.openStream(key));
        assertTrue(ex.getMessage().contains("corrupted payload or invalid authentication tag"));
    }

    @Test
    @DisplayName("Tampered header metadata triggers SecurityException via AAD authentication")
    void tamperedHeaderThrowsSecurityException() throws Exception {
        String key = "tamper/header.bin";
        byte[] inputBytes = SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8);

        encryptedStorage.store(key, new ByteArrayInputStream(inputBytes), inputBytes.length);

        // Corrupt one byte in header (version byte at offset 4)
        Path diskPath = filesystemStorage.resolveSecurePath(key);
        byte[] diskBytes = Files.readAllBytes(diskPath);
        diskBytes[4] = 99; // invalid version
        Files.write(diskPath, diskBytes);

        SecurityException ex = assertThrows(SecurityException.class, () -> encryptedStorage.openStream(key));
        assertTrue(ex.getMessage().contains("Unsupported artifact envelope encryption version"));
    }

    @Test
    @DisplayName("Rotated keys support: documents encrypted under prior key can still be decrypted")
    void keyRotationSupport() throws Exception {
        String key = "rotation/doc.bin";
        byte[] inputBytes = "Rotated secret document".getBytes(StandardCharsets.UTF_8);

        // Store with primary-key
        encryptedStorage.store(key, new ByteArrayInputStream(inputBytes), inputBytes.length);

        // Create new provider with active rotated key 'v2-key', but keeping 'primary-key' registered
        String v2KeyMaterial = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
        EnvironmentStorageKeyProvider rotatedProvider = new EnvironmentStorageKeyProvider("v2-key", v2KeyMaterial);
        rotatedProvider.registerKey("primary-key", MASTER_KEY);

        EncryptedDocumentArtifactStorage rotatedStorage =
                new EncryptedDocumentArtifactStorage(filesystemStorage, rotatedProvider, true);

        // Decrypt document originally encrypted under primary-key
        Optional<InputStream> streamOpt = rotatedStorage.openStream(key);
        assertTrue(streamOpt.isPresent());
        try (InputStream in = streamOpt.get()) {
            assertEquals("Rotated secret document", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("When encryption is disabled, store writes raw plaintext without ZKDE header")
    void disabledEncryptionPassesThrough() throws Exception {
        EncryptedDocumentArtifactStorage unencrypted =
                new EncryptedDocumentArtifactStorage(filesystemStorage, keyProvider, false);

        String key = "passthrough/plain.txt";
        byte[] inputBytes = "Plaintext passthrough".getBytes(StandardCharsets.UTF_8);

        unencrypted.store(key, new ByteArrayInputStream(inputBytes), inputBytes.length);

        Optional<InputStream> rawOpt = filesystemStorage.openStream(key);
        assertTrue(rawOpt.isPresent());
        try (InputStream in = rawOpt.get()) {
            byte[] raw = in.readAllBytes();
            byte[] magic = Arrays.copyOfRange(raw, 0, EncryptedDocumentArtifactStorage.MAGIC_BYTES.length);
            assertFalse(Arrays.equals(EncryptedDocumentArtifactStorage.MAGIC_BYTES, magic));
            assertEquals("Plaintext passthrough", new String(raw, StandardCharsets.UTF_8));
        }
    }
}

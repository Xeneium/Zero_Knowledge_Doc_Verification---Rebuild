package com.zkdoc.document.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

/**
 * Hardened filesystem-backed implementation of DocumentArtifactStorage.
 * Enforces:
 * 1. Strict path traversal prevention (rejects escaping the base directory).
 * 2. Atomic write-and-rename guarantees (prevents partial file reads during write).
 * 3. Streaming SHA-256 checksum calculation and integrity verification.
 * 4. Safe temporary file cleanup on failures.
 */
public class FilesystemDocumentArtifactStorage implements DocumentArtifactStorage {

    private final Path baseDirectory;

    public FilesystemDocumentArtifactStorage(Path baseDirectory) {
        Objects.requireNonNull(baseDirectory, "baseDirectory must not be null");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize artifact storage base directory: " + this.baseDirectory, e);
        }
    }

    public FilesystemDocumentArtifactStorage(String baseDirectoryPath) {
        this(Paths.get(Objects.requireNonNull(baseDirectoryPath, "baseDirectoryPath must not be null")));
    }

    @Override
    public long store(String storageKey, InputStream inputStream, long expectedSize) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Path targetPath = resolveSecurePath(storageKey);

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent != null ? parent : baseDirectory, "upload-", ".tmp");
        long bytesWritten = 0;

        try {
            MessageDigest digest = createSha256Digest();
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile, StandardOpenOption.WRITE));
                 InputStream in = new BufferedInputStream(inputStream)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    bytesWritten += read;
                }
                out.flush();
            }

            if (expectedSize >= 0 && bytesWritten != expectedSize) {
                throw new IOException("Stream size mismatch: expected " + expectedSize + " bytes, wrote " + bytesWritten);
            }

            // Atomic rename to target location
            Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return bytesWritten;
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Best effort temp cleanup
            }
        }
    }

    @Override
    public Optional<InputStream> openStream(String storageKey) throws IOException {
        Path targetPath = resolveSecurePath(storageKey);
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            return Optional.empty();
        }
        return Optional.of(new BufferedInputStream(Files.newInputStream(targetPath, StandardOpenOption.READ)));
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            Path targetPath = resolveSecurePath(storageKey);
            return Files.exists(targetPath) && Files.isRegularFile(targetPath);
        } catch (IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    @Override
    public boolean delete(String storageKey) throws IOException {
        Path targetPath = resolveSecurePath(storageKey);
        return Files.deleteIfExists(targetPath);
    }

    /**
     * Verifies that the stored artifact exists and matches the expected SHA-256 digest.
     */
    public boolean verifyIntegrity(String storageKey, byte[] expectedSha256) throws IOException {
        Objects.requireNonNull(expectedSha256, "expectedSha256 must not be null");
        Path targetPath = resolveSecurePath(storageKey);
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            return false;
        }

        MessageDigest digest = createSha256Digest();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(targetPath, StandardOpenOption.READ))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return MessageDigest.isEqual(digest.digest(), expectedSha256);
    }

    /**
     * Resolves and normalizes a storage key against the base directory, strictly preventing path traversal.
     */
    public Path resolveSecurePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be null or blank");
        }

        if (storageKey.contains("\0")) {
            throw new SecurityException("Null byte detected in storage key");
        }

        // Normalize separators
        String cleanKey = storageKey.replace('\\', '/').trim();
        while (cleanKey.startsWith("/")) {
            cleanKey = cleanKey.substring(1);
        }

        Path resolved = baseDirectory.resolve(cleanKey).normalize().toAbsolutePath();
        if (!resolved.startsWith(baseDirectory)) {
            throw new SecurityException("Path traversal attempt detected: key '" + storageKey + "' resolves outside storage root");
        }

        return resolved;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public long getAvailableSpace() throws IOException {
        return Files.getFileStore(baseDirectory).getUsableSpace();
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}

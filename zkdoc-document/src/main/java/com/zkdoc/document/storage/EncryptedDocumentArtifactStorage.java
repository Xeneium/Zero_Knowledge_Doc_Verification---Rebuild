package com.zkdoc.document.storage;

import com.zkdoc.document.storage.key.StorageKeyProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Production envelope encryption decorator for DocumentArtifactStorage implementing AES-256-GCM (FIND-003).
 * <p>
 * Key Security Properties:
 * 1. Authenticated Encryption with Associated Data (AEAD) using AES-256-GCM with a 128-bit authentication tag.
 * 2. Per-file cryptographic freshness using a unique 96-bit (12-byte) random IV generated via SecureRandom.
 * 3. Cryptographically bound header metadata (Magic bytes, version, keyId, IV) via GCM AAD.
 * 4. Transparent backward compatibility: legacy unencrypted artifacts lacking the magic header are read directly.
 * 5. Tamper detection: any bit flip or tag mismatch throws a SecurityException, preventing corrupted retrieval.
 */
public class EncryptedDocumentArtifactStorage implements DocumentArtifactStorage {

    public static final byte[] MAGIC_BYTES = new byte[] {0x5A, 0x4B, 0x44, 0x45}; // "ZKDE"
    public static final byte FORMAT_VERSION = 1;
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int GCM_IV_LENGTH_BYTES = 12;

    private final DocumentArtifactStorage delegate;
    private final StorageKeyProvider keyProvider;
    private final boolean encryptionEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedDocumentArtifactStorage(
            DocumentArtifactStorage delegate,
            StorageKeyProvider keyProvider,
            boolean encryptionEnabled) {
        this.delegate = Objects.requireNonNull(delegate, "delegate storage must not be null");
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.encryptionEnabled = encryptionEnabled;
    }

    @Override
    public long store(String storageKey, InputStream inputStream, long expectedSize) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");

        if (!encryptionEnabled) {
            return delegate.store(storageKey, inputStream, expectedSize);
        }

        byte[] plaintext = inputStream.readAllBytes();
        if (expectedSize >= 0 && plaintext.length != expectedSize) {
            throw new IOException("Stream size mismatch: expected " + expectedSize + " bytes, read " + plaintext.length);
        }

        String activeKeyId = keyProvider.getActiveKeyId();
        SecretKey secretKey = keyProvider.getKey(activeKeyId)
                .orElseThrow(() -> new IllegalStateException("Active storage encryption key not found for ID: " + activeKeyId));

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        byte[] keyIdBytes = activeKeyId.getBytes(StandardCharsets.UTF_8);
        if (keyIdBytes.length > 65535) {
            throw new IllegalArgumentException("keyId exceeds maximum allowable length of 65535 bytes");
        }

        // Header construction: MAGIC (4) + VERSION (1) + KEY_ID_LEN (2) + KEY_ID + IV (12)
        int headerLength = 4 + 1 + 2 + keyIdBytes.length + GCM_IV_LENGTH_BYTES;
        ByteBuffer headerBuffer = ByteBuffer.allocate(headerLength);
        headerBuffer.put(MAGIC_BYTES);
        headerBuffer.put(FORMAT_VERSION);
        headerBuffer.putShort((short) keyIdBytes.length);
        headerBuffer.put(keyIdBytes);
        headerBuffer.put(iv);
        byte[] headerBytes = headerBuffer.array();

        byte[] ciphertext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(headerBytes);
            ciphertext = cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to encrypt document artifact using AES-256-GCM", e);
        }

        ByteArrayOutputStream combined = new ByteArrayOutputStream(headerBytes.length + ciphertext.length);
        combined.write(headerBytes);
        combined.write(ciphertext);
        byte[] encryptedPayload = combined.toByteArray();

        delegate.store(storageKey, new ByteArrayInputStream(encryptedPayload), encryptedPayload.length);
        return plaintext.length;
    }

    @Override
    public Optional<InputStream> openStream(String storageKey) throws IOException {
        Optional<InputStream> rawStreamOpt = delegate.openStream(storageKey);
        if (rawStreamOpt.isEmpty()) {
            return Optional.empty();
        }

        byte[] payload;
        try (InputStream rawStream = rawStreamOpt.get()) {
            payload = rawStream.readAllBytes();
        }

        // Check if file is encrypted (starts with ZKDE magic bytes and has minimum header size)
        if (isEncryptedPayload(payload)) {
            byte[] decrypted = decryptPayload(payload, storageKey);
            return Optional.of(new ByteArrayInputStream(decrypted));
        }

        // Legacy unencrypted plaintext fallback for seamless backward compatibility
        return Optional.of(new ByteArrayInputStream(payload));
    }

    @Override
    public boolean exists(String storageKey) {
        return delegate.exists(storageKey);
    }

    @Override
    public boolean delete(String storageKey) throws IOException {
        return delegate.delete(storageKey);
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public DocumentArtifactStorage getDelegate() {
        return delegate;
    }

    private boolean isEncryptedPayload(byte[] payload) {
        if (payload == null || payload.length < 4 + 1 + 2 + 1 + GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            return false;
        }
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (payload[i] != MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] decryptPayload(byte[] payload, String storageKey) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(payload);

        // Skip 4 bytes magic
        buf.position(4);
        byte version = buf.get();
        if (version != FORMAT_VERSION) {
            throw new SecurityException("Unsupported artifact envelope encryption version: " + version);
        }

        int keyIdLen = Short.toUnsignedInt(buf.getShort());
        if (buf.remaining() < keyIdLen + GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw new SecurityException("Malformed encrypted artifact header: insufficient payload length");
        }

        byte[] keyIdBytes = new byte[keyIdLen];
        buf.get(keyIdBytes);
        String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        buf.get(iv);

        int headerLength = 4 + 1 + 2 + keyIdLen + GCM_IV_LENGTH_BYTES;
        byte[] headerBytes = Arrays.copyOfRange(payload, 0, headerLength);
        byte[] ciphertext = Arrays.copyOfRange(payload, headerLength, payload.length);

        SecretKey secretKey = keyProvider.getKey(keyId)
                .orElseThrow(() -> new SecurityException("Artifact decryption key not available for key ID: " + keyId));

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(headerBytes);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Artifact ciphertext decryption or authentication failed for key '"
                    + storageKey + "': corrupted payload or invalid authentication tag", e);
        }
    }
}

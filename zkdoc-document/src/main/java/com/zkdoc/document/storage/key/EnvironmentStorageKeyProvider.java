package com.zkdoc.document.storage.key;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Environment- and properties-backed StorageKeyProvider for AES-256-GCM envelope encryption.
 * Accepts keys encoded as Hex (64 hex characters), Base64 (44 chars), or derives 256 bits via SHA-256.
 * Supports active key rotation and multi-key decryption lookup.
 */
public class EnvironmentStorageKeyProvider implements StorageKeyProvider {

    private final String activeKeyId;
    private final Map<String, SecretKey> keyMap = new ConcurrentHashMap<>();

    public EnvironmentStorageKeyProvider(String activeKeyId, String rawKeyMaterial) {
        this.activeKeyId = Objects.requireNonNullElse(activeKeyId, "primary-storage-key");

        SecretKey activeKey;
        if (rawKeyMaterial == null || rawKeyMaterial.isBlank()) {
            // Generate ephemeral key for tests / non-configured environments
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            activeKey = new SecretKeySpec(ephemeral, "AES");
        } else {
            activeKey = parseSecretKey(rawKeyMaterial.trim());
        }

        keyMap.put(this.activeKeyId, activeKey);
    }

    public EnvironmentStorageKeyProvider(String activeKeyId, SecretKey activeKey) {
        this.activeKeyId = Objects.requireNonNull(activeKeyId, "activeKeyId must not be null");
        Objects.requireNonNull(activeKey, "activeKey must not be null");
        this.keyMap.put(activeKeyId, activeKey);
    }

    public void registerKey(String keyId, SecretKey key) {
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        keyMap.put(keyId, key);
    }

    public void registerKey(String keyId, String rawKeyMaterial) {
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(rawKeyMaterial, "rawKeyMaterial must not be null");
        keyMap.put(keyId, parseSecretKey(rawKeyMaterial.trim()));
    }

    @Override
    public String getActiveKeyId() {
        return activeKeyId;
    }

    @Override
    public Optional<SecretKey> getKey(String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(keyMap.get(keyId));
    }

    private static SecretKey parseSecretKey(String material) {
        byte[] keyBytes;
        // 1. Try 64-char Hex (32 bytes = 256 bits)
        if (material.length() == 64 && material.matches("^[0-9a-fA-F]+$")) {
            keyBytes = HexFormat.of().parseHex(material);
        } else {
            // 2. Try Base64
            try {
                byte[] decoded = Base64.getDecoder().decode(material);
                if (decoded.length == 32) {
                    keyBytes = decoded;
                } else {
                    keyBytes = derive256BitKey(material);
                }
            } catch (IllegalArgumentException e) {
                // 3. Fallback to SHA-256 derivation of string secret
                keyBytes = derive256BitKey(material);
            }
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] derive256BitKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available in JVM", e);
        }
    }
}

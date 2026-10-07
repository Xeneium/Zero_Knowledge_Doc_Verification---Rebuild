package com.zkdoc.keys;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable SHA-256 fingerprint computed strictly from canonical X.509
 * SubjectPublicKeyInfo DER-encoded byte sequence.
 */
public record KeyFingerprint(String value) {

    public static final String PREFIX = "SHA256:";

    public KeyFingerprint {
        Objects.requireNonNull(value, "fingerprint value must not be null");
        if (!value.startsWith(PREFIX)) {
            value = PREFIX + value;
        }
        String hex = value.substring(PREFIX.length());
        if (hex.length() != 64) {
            throw new IllegalArgumentException("SHA-256 fingerprint hex must be exactly 64 characters, found: " + hex.length());
        }
    }

    public static KeyFingerprint fromPublicKey(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        return fromDer(publicKey.getEncoded());
    }

    public static KeyFingerprint fromDer(byte[] derBytes) {
        Objects.requireNonNull(derBytes, "derBytes must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(derBytes);
            return new KeyFingerprint(PREFIX + HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", e);
        }
    }

    public String hex() {
        return value.substring(PREFIX.length());
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.zkdoc.core.crypto;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable value object representing a 256-bit (32-byte) cryptographic digest (e.g. SHA-256).
 * Ensures exact length validation, defensive byte copying, constant-time equality comparisons,
 * and deterministic lowercase hexadecimal representation.
 */
public final class Digest {
    public static final int DIGEST_LENGTH_BYTES = 32;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final byte[] bytes;

    private Digest(byte[] rawBytes) {
        Objects.requireNonNull(rawBytes, "rawBytes must not be null");
        if (rawBytes.length != DIGEST_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "Digest length must be exactly " + DIGEST_LENGTH_BYTES + " bytes, got " + rawBytes.length);
        }
        this.bytes = rawBytes.clone();
    }

    /**
     * Constructs a Digest from raw 32-byte array.
     */
    public static Digest of(byte[] rawBytes) {
        return new Digest(rawBytes);
    }

    /**
     * Parses a 64-character lowercase or uppercase hexadecimal string into a Digest.
     */
    public static Digest fromHex(String hexString) {
        Objects.requireNonNull(hexString, "hexString must not be null");
        String trimmed = hexString.trim();
        if (trimmed.length() != DIGEST_LENGTH_BYTES * 2) {
            throw new IllegalArgumentException(
                    "Digest hex string must be exactly " + (DIGEST_LENGTH_BYTES * 2) + " characters, got " + trimmed.length());
        }
        return new Digest(HEX_FORMAT.parseHex(trimmed));
    }

    /**
     * Returns a defensive copy of the raw 32-byte digest.
     */
    public byte[] getBytes() {
        return bytes.clone();
    }

    /**
     * Returns the lowercase 64-character hexadecimal representation.
     */
    public String toHex() {
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * Performs constant-time comparison against candidate bytes to resist timing side-channel attacks.
     */
    public boolean matches(byte[] candidate) {
        if (candidate == null || candidate.length != DIGEST_LENGTH_BYTES) {
            return false;
        }
        return MessageDigest.isEqual(this.bytes, candidate);
    }

    /**
     * Performs constant-time comparison against another Digest.
     */
    public boolean matches(Digest other) {
        if (other == null) {
            return false;
        }
        return MessageDigest.isEqual(this.bytes, other.bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Digest digest = (Digest) o;
        return MessageDigest.isEqual(bytes, digest.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "Digest[" + toHex() + "]";
    }
}

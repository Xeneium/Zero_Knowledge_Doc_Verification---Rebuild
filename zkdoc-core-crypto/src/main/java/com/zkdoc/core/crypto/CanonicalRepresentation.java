package com.zkdoc.core.crypto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value object holding the output of a deterministic canonicalization procedure.
 * Includes the canonical format version and defensive byte array access.
 */
public final class CanonicalRepresentation {
    private final int version;
    private final byte[] canonicalBytes;

    public CanonicalRepresentation(int version, byte[] canonicalBytes) {
        if (version <= 0) {
            throw new IllegalArgumentException("Canonical format version must be positive, got: " + version);
        }
        Objects.requireNonNull(canonicalBytes, "canonicalBytes must not be null");
        this.version = version;
        this.canonicalBytes = canonicalBytes.clone();
    }

    public int version() {
        return version;
    }

    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    public int length() {
        return canonicalBytes.length;
    }

    public Digest computeDigest(DigestService digestService) {
        Objects.requireNonNull(digestService, "digestService must not be null");
        return Digest.of(digestService.sha256(canonicalBytes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalRepresentation that = (CanonicalRepresentation) o;
        return version == that.version && Arrays.equals(canonicalBytes, that.canonicalBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version);
        result = 31 * result + Arrays.hashCode(canonicalBytes);
        return result;
    }

    @Override
    public String toString() {
        return "CanonicalRepresentation[version=" + version + ", length=" + canonicalBytes.length + " bytes]";
    }
}

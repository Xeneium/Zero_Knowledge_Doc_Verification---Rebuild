package com.zkdoc.zkp.engine;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value object representing a generated Bulletproof predicate proof artifact.
 * <p>
 * Contains the canonical binary representation, protocol version, and bit-width metadata.
 */
public record PredicateProofArtifact(
        String proofVersion,
        int bitSize,
        byte[] proofBytes
) {
    public static final int MAX_PROOF_BYTES = 64 * 1024; // 64 KB safety bound

    public PredicateProofArtifact {
        Objects.requireNonNull(proofVersion, "proofVersion must not be null");
        Objects.requireNonNull(proofBytes, "proofBytes must not be null");
        if (proofBytes.length == 0) {
            throw new IllegalArgumentException("Proof bytes cannot be empty");
        }
        if (proofBytes.length > MAX_PROOF_BYTES) {
            throw new IllegalArgumentException("Proof size exceeds maximum limit of " + MAX_PROOF_BYTES + " bytes: " + proofBytes.length);
        }
        if (bitSize <= 0 || bitSize > 128) {
            throw new IllegalArgumentException("Unsupported bit size: " + bitSize);
        }
        proofBytes = proofBytes.clone();
    }

    @Override
    public byte[] proofBytes() {
        return proofBytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PredicateProofArtifact that)) return false;
        return bitSize == that.bitSize &&
                proofVersion.equals(that.proofVersion) &&
                Arrays.equals(proofBytes, that.proofBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(proofVersion, bitSize);
        result = 31 * result + Arrays.hashCode(proofBytes);
        return result;
    }

    @Override
    public String toString() {
        return "PredicateProofArtifact{" +
                "proofVersion='" + proofVersion + '\'' +
                ", bitSize=" + bitSize +
                ", proofBytesLength=" + proofBytes.length +
                '}';
    }
}

package com.zkdoc.core.crypto;

/**
 * Contract for creating and manipulating homomorphic Pedersen commitments
 * over prime-order elliptic curve groups (e.g., Ristretto255: C = v*G + r*H).
 */
public interface CommitmentService {

    /**
     * Generates a 32-byte Pedersen commitment point C = v*G + r*H.
     *
     * @param value Integer attribute value (unsigned 64-bit domain)
     * @param blindingFactor 32-byte cryptographically random scalar
     * @return 32-byte compressed curve point
     */
    byte[] commit(long value, byte[] blindingFactor);

    /**
     * Computes the shifted commitment point C' = C - threshold*G.
     * This homomorphically reduces the predicate (value >= threshold) to (value - threshold in [0, 2^64-1]).
     *
     * @param originalCommitment 32-byte original commitment point C
     * @param threshold Value to shift
     * @return 32-byte shifted commitment point C'
     */
    byte[] shiftCommitment(byte[] originalCommitment, long threshold);
}

package com.zkdoc.core.crypto;

/**
 * Service Provider Interface (SPI) isolating the zero-knowledge Bulletproof range proof engine.
 * Decouples platform domain logic from third-party cryptographic mathematical libraries.
 */
public interface RangeProofEngine {

    /**
     * Generates a zero-knowledge Bulletproof range proof asserting that the committed
     * value is non-negative and within [0, 2^64 - 1].
     *
     * @param value Private integer value
     * @param blindingFactor Private 32-byte blinding scalar
     * @param transcriptBinding Public contextual bytes absorbed into Fiat-Shamir transcript
     * @return Serialized proof byte array
     */
    byte[] proveRange(long value, byte[] blindingFactor, byte[] transcriptBinding);

    /**
     * Verifies a Bulletproof range proof over a shifted commitment point C'.
     *
     * @param shiftedCommitment 32-byte compressed commitment point C'
     * @param proofBytes Serialized proof byte array
     * @param transcriptBinding Public contextual bytes absorbed into Fiat-Shamir transcript
     * @return true if and only if the proof is mathematically valid
     */
    boolean verifyRange(byte[] shiftedCommitment, byte[] proofBytes, byte[] transcriptBinding);
}

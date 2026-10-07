package com.zkdoc.zkp.engine;

/**
 * Project-level cryptographic abstraction governing Bulletproof-compatible predicate proofs.
 * <p>
 * Decouples domain and proving workflows from third-party library internals (e.g. Weavechain Bulletproofs).
 * The engine operates solely over strongly-typed cryptographic and domain primitives.
 */
public interface BulletproofPredicateEngine {

    /** Default protocol version identifier */
    String PROTOCOL_VERSION = "zkdoc-range-v1";

    /** Default bit range width: 64-bit integer relations [0, 2^64 - 1] */
    int DEFAULT_BIT_SIZE = 64;

    /**
     * Proves that the committed value opens to \( v \ge \text{threshold} \).
     *
     * @param value Private plaintext attribute value
     * @param blindingFactor Private 32-byte blinding scalar
     * @param threshold Public threshold boundary
     * @param transcriptNonce Public contextual nonce / challenge bytes for Fiat-Shamir transcript
     * @return Immutable {@link PredicateProofArtifact} containing serialized proof and commitments
     * @throws IllegalArgumentException if \( v < \text{threshold} \) or invalid inputs
     */
    PredicateProofArtifact proveGreaterThanOrEqual(
            long value,
            byte[] blindingFactor,
            long threshold,
            byte[] transcriptNonce);

    /**
     * Cryptographically verifies that a {@link PredicateProofArtifact} demonstrates that
     * the underlying committed value in {@code commitmentPoint} satisfies \( v \ge \text{threshold} \).
     *
     * @param commitmentPoint 32-byte compressed curve point of the authentic commitment C
     * @param threshold Public threshold boundary
     * @param transcriptNonce Public contextual nonce / challenge bytes
     * @param artifact Proof artifact to verify
     * @return true if and only if the proof is cryptographically sound and verified
     */
    boolean verifyGreaterThanOrEqual(
            byte[] commitmentPoint,
            long threshold,
            byte[] transcriptNonce,
            PredicateProofArtifact artifact);
}

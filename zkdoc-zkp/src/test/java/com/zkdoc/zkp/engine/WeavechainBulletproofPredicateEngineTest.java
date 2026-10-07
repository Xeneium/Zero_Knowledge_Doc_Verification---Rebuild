package com.zkdoc.zkp.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofs;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeavechainBulletproofPredicateEngineTest {

    private WeavechainBulletproofPredicateEngine engine;
    private PedersenCommitment pedersen;

    @BeforeEach
    void setUp() throws Exception {
        engine = new WeavechainBulletproofPredicateEngine();
        pedersen = PedersenCommitment.getDefault();
    }

    @Test
    @DisplayName("Cryptographic Success: Proves and verifies exact threshold (v == threshold)")
    void exactThresholdProvesAndVerifies() {
        long value = 18L;
        long threshold = 18L;
        byte[] blinding = randomBlinding();
        byte[] commitment = computeCommitment(value, blinding);
        byte[] nonce = "nonce-exact-18".getBytes();

        PredicateProofArtifact artifact = engine.proveGreaterThanOrEqual(value, blinding, threshold, nonce);

        assertNotNull(artifact);
        assertEquals(BulletproofPredicateEngine.PROTOCOL_VERSION, artifact.proofVersion());
        assertEquals(64, artifact.bitSize());
        assertTrue(artifact.proofBytes().length > 0);

        boolean verified = engine.verifyGreaterThanOrEqual(commitment, threshold, nonce, artifact);
        assertTrue(verified, "Proof for v == threshold must cryptographically verify");
    }

    @Test
    @DisplayName("Cryptographic Success: Proves and verifies above threshold (v > threshold)")
    void aboveThresholdProvesAndVerifies() {
        long value = 25L;
        long threshold = 18L;
        byte[] blinding = randomBlinding();
        byte[] commitment = computeCommitment(value, blinding);
        byte[] nonce = "nonce-above-18".getBytes();

        PredicateProofArtifact artifact = engine.proveGreaterThanOrEqual(value, blinding, threshold, nonce);

        boolean verified = engine.verifyGreaterThanOrEqual(commitment, threshold, nonce, artifact);
        assertTrue(verified, "Proof for v > threshold must cryptographically verify");
    }

    @Test
    @DisplayName("Cryptographic Failure: Fails proof generation when v < threshold")
    void belowThresholdFailsProofGeneration() {
        long value = 17L;
        long threshold = 18L;
        byte[] blinding = randomBlinding();
        byte[] nonce = "nonce-below-18".getBytes();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                engine.proveGreaterThanOrEqual(value, blinding, threshold, nonce));
        assertTrue(ex.getMessage().contains("Cannot generate proof for unsatisfied predicate"));
    }

    @Test
    @DisplayName("Input Validation: Negative values or thresholds rejected immediately")
    void negativeValuesRejected() {
        byte[] blinding = randomBlinding();
        byte[] nonce = "nonce-neg".getBytes();

        assertThrows(IllegalArgumentException.class, () ->
                engine.proveGreaterThanOrEqual(-1L, blinding, 18L, nonce));

        assertThrows(IllegalArgumentException.class, () ->
                engine.proveGreaterThanOrEqual(25L, blinding, -1L, nonce));
    }

    @Test
    @DisplayName("Adversarial Malleability: Modifying proof bytes causes cryptographic verification failure")
    void tamperedProofBytesFailsVerification() {
        long value = 30L;
        long threshold = 21L;
        byte[] blinding = randomBlinding();
        byte[] commitment = computeCommitment(value, blinding);
        byte[] nonce = "nonce-tamper".getBytes();

        PredicateProofArtifact artifact = engine.proveGreaterThanOrEqual(value, blinding, threshold, nonce);

        // Tamper with the proof bytes
        byte[] tamperedBytes = artifact.proofBytes();
        tamperedBytes[tamperedBytes.length - 1] ^= 0x01;
        PredicateProofArtifact tamperedArtifact = new PredicateProofArtifact(
                artifact.proofVersion(), artifact.bitSize(), tamperedBytes);

        boolean verified = engine.verifyGreaterThanOrEqual(commitment, threshold, nonce, tamperedArtifact);
        assertFalse(verified, "Tampered proof must fail verification closed");
    }

    @Test
    @DisplayName("Adversarial Binding: Using proof against wrong threshold fails verification")
    void wrongThresholdFailsVerification() {
        long value = 25L;
        long provedThreshold = 18L;
        byte[] blinding = randomBlinding();
        byte[] commitment = computeCommitment(value, blinding);
        byte[] nonce = "nonce-thresh".getBytes();

        PredicateProofArtifact artifact = engine.proveGreaterThanOrEqual(value, blinding, provedThreshold, nonce);

        // Verifier checks for threshold 26
        boolean verified = engine.verifyGreaterThanOrEqual(commitment, 26L, nonce, artifact);
        assertFalse(verified, "Proof generated for threshold 18 must not verify for threshold 26");

        // Verifier checks for threshold 21 (different R1CS public input)
        boolean verified21 = engine.verifyGreaterThanOrEqual(commitment, 21L, nonce, artifact);
        assertFalse(verified21, "Proof generated for threshold 18 must not verify for threshold 21");
    }

    @Test
    @DisplayName("Adversarial Binding: Using proof against wrong commitment point fails verification")
    void wrongCommitmentFailsVerification() {
        long valueA = 25L;
        long valueB = 30L;
        long threshold = 18L;
        byte[] blindingA = randomBlinding();
        byte[] blindingB = randomBlinding();
        byte[] commitmentA = computeCommitment(valueA, blindingA);
        byte[] commitmentB = computeCommitment(valueB, blindingB);
        byte[] nonce = "nonce-comm".getBytes();

        PredicateProofArtifact artifactA = engine.proveGreaterThanOrEqual(valueA, blindingA, threshold, nonce);

        // Verification of proof A against commitment B must fail closed
        boolean verified = engine.verifyGreaterThanOrEqual(commitmentB, threshold, nonce, artifactA);
        assertFalse(verified, "Proof for commitment A must not verify against commitment B");
    }

    @Test
    @DisplayName("Adversarial Replay: Replaying proof under different transcript nonce fails verification")
    void nonceMismatchFailsVerification() {
        long value = 25L;
        long threshold = 18L;
        byte[] blinding = randomBlinding();
        byte[] commitment = computeCommitment(value, blinding);
        byte[] nonce1 = "nonce-session-1".getBytes();
        byte[] nonce2 = "nonce-session-2".getBytes();

        PredicateProofArtifact artifact = engine.proveGreaterThanOrEqual(value, blinding, threshold, nonce1);

        boolean verified = engine.verifyGreaterThanOrEqual(commitment, threshold, nonce2, artifact);
        assertFalse(verified, "Replayed proof with different transcript nonce must fail verification");
    }

    @Test
    @DisplayName("Boundary Range: Upper 64-bit integer domain boundaries")
    void largeIntegerBoundaryTests() {
        long largeThreshold = 1_000_000_000L;
        long largeValue = 1_000_000_001L;
        byte[] blinding = randomBlinding();
        byte[] commitment = computeCommitment(largeValue, blinding);
        byte[] nonce = "nonce-large".getBytes();

        PredicateProofArtifact artifact = engine.proveGreaterThanOrEqual(largeValue, blinding, largeThreshold, nonce);
        boolean verified = engine.verifyGreaterThanOrEqual(commitment, largeThreshold, nonce, artifact);
        assertTrue(verified, "Large integer values within [0, 2^64-1] must verify cleanly");
    }

    @Test
    @DisplayName("Malformed inputs: Truncated or empty proof bytes fail safely without crashing")
    void malformedProofBytesFailSafely() {
        byte[] commitment = computeCommitment(25L, randomBlinding());
        byte[] nonce = "nonce".getBytes();

        assertFalse(engine.verifyGreaterThanOrEqual(commitment, 18L, nonce, null));

        PredicateProofArtifact malformed = new PredicateProofArtifact(
                BulletproofPredicateEngine.PROTOCOL_VERSION, 64, new byte[] {0x01, 0x02, 0x03});
        assertFalse(engine.verifyGreaterThanOrEqual(commitment, 18L, nonce, malformed));
    }

    private byte[] randomBlinding() {
        return Utils.scalar(123456789L).toByteArray();
    }

    private byte[] computeCommitment(long value, byte[] blindingBytes) {
        Scalar valScalar = Utils.scalar(value);
        Scalar blindScalar = BulletProofs.getFactory().fromBits(blindingBytes);
        return pedersen.commit(valScalar, blindScalar).toByteArray();
    }
}

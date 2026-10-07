package com.zkdoc.zkp.engine;

import com.weavechain.ec.ECPoint;
import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.Allocated;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.BulletProofs;
import com.weavechain.zk.bulletproofs.Commitment;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Proof;
import com.weavechain.zk.bulletproofs.Prover;
import com.weavechain.zk.bulletproofs.Transcript;
import com.weavechain.zk.bulletproofs.Utils;
import com.weavechain.zk.bulletproofs.Variable;
import com.weavechain.zk.bulletproofs.Verifier;
import com.weavechain.zk.bulletproofs.gadgets.NumberIsGreaterOrEqual;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import com.zkdoc.zkp.CredentialPredicateProver;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link BulletproofPredicateEngine} wrapping
 * Weavechain Bulletproof R1CS gadgets over Curve25519 / Ristretto.
 * <p>
 * Evaluates the relation:
 * \[
 * v \ge \text{threshold} \iff (v - \text{threshold}) \in [0, 2^{64}-1]
 * \]
 * bound to the authentic Pedersen commitment \( C = vG + rH \).
 */
@Component
public class WeavechainBulletproofPredicateEngine implements BulletproofPredicateEngine {

    private static final Logger log = LoggerFactory.getLogger(WeavechainBulletproofPredicateEngine.class);
    private static final int GENERATOR_CAPACITY = 128;
    private static final int PARTY_COUNT = 1;

    private final PedersenCommitment pedersenCommitment;
    private final NumberIsGreaterOrEqual predicateGadget;

    public WeavechainBulletproofPredicateEngine() {
        try {
            this.pedersenCommitment = PedersenCommitment.getDefault();
            this.predicateGadget = new NumberIsGreaterOrEqual();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to initialize Weavechain Bulletproof generators", exception);
        }
    }

    private BulletProofGenerators createGenerators() {
        try {
            return new BulletProofGenerators(GENERATOR_CAPACITY, PARTY_COUNT);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate BulletProofGenerators", e);
        }
    }

    PedersenCommitment getPedersenCommitment() {
        return pedersenCommitment;
    }

    BulletProofGenerators getGenerators() {
        return createGenerators();
    }

    @Override
    public PredicateProofArtifact proveGreaterThanOrEqual(
            long value,
            byte[] blindingFactor,
            long threshold,
            byte[] transcriptNonce) {

        Objects.requireNonNull(blindingFactor, "blindingFactor must not be null");
        Objects.requireNonNull(transcriptNonce, "transcriptNonce must not be null");

        if (blindingFactor.length == 0) {
            throw new IllegalArgumentException("Blinding factor cannot be empty");
        }
        if (transcriptNonce.length == 0) {
            throw new IllegalArgumentException("Transcript nonce cannot be empty");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Negative value is outside the supported range: " + value);
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("Negative threshold is not supported: " + threshold);
        }
        if (value < threshold) {
            throw new IllegalArgumentException(
                    "Cannot generate proof for unsatisfied predicate: value " + value + " < threshold " + threshold);
        }

        try {
            Scalar blinding = BulletProofs.getFactory().fromBits(blindingFactor);
            CredentialPredicateProver cppProver = new CredentialPredicateProver(pedersenCommitment, createGenerators());
            byte[] serializedProof = cppProver.prove(value, blinding, threshold, transcriptNonce);
            return new PredicateProofArtifact(PROTOCOL_VERSION, DEFAULT_BIT_SIZE, serializedProof);

        } catch (RuntimeException e) {
            log.error("Bulletproof proving failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean verifyGreaterThanOrEqual(
            byte[] commitmentPoint,
            long threshold,
            byte[] transcriptNonce,
            PredicateProofArtifact artifact) {

        if (commitmentPoint == null || commitmentPoint.length == 0 ||
                transcriptNonce == null || transcriptNonce.length == 0 ||
                artifact == null || artifact.proofBytes().length == 0 ||
                threshold < 0) {
            return false;
        }

        if (!PROTOCOL_VERSION.equals(artifact.proofVersion()) || artifact.bitSize() != DEFAULT_BIT_SIZE) {
            log.debug("Verification rejected due to unsupported protocol version or bit size");
            return false;
        }

        try {
            Proof proof = Proof.deserialize(artifact.proofBytes());

            // We require exactly two commitments: C_value and C_difference
            if (proof.getCommitments() == null || proof.getCommitments().size() != 2) {
                return false;
            }

            // Commitment 0 must match the authentic registered commitment point C exactly
            byte[] provedCommitmentBytes = proof.getCommitment(0).toByteArray();
            if (!MessageDigest.isEqual(provedCommitmentBytes, commitmentPoint)) {
                log.debug("Proved commitment does not match authentic commitment anchor point");
                return false;
            }

            // Initialize verifier with matching Fiat-Shamir transcript
            Transcript transcript = initializeTranscript(transcriptNonce);
            Verifier verifier = new Verifier(transcript);

            ECPoint authenticCommitmentPoint = BulletProofs.getFactory().fromCompressed(commitmentPoint);
            Variable valueVariable = verifier.commit(authenticCommitmentPoint);
            Variable differenceVariable = verifier.commit(proof.getCommitment(1));

            // Constrain verifier gadget
            boolean checkGreater = predicateGadget.checkGreater(
                    verifier,
                    new Allocated(valueVariable, null),
                    new Allocated(differenceVariable, null),
                    BigInteger.valueOf(threshold),
                    DEFAULT_BIT_SIZE);
            if (!checkGreater) {
                return false;
            }

            return verifier.verify(proof, pedersenCommitment, createGenerators());

        } catch (Exception e) {
            log.error("Bulletproof verification encountered exception (failing closed): {}", e.getMessage(), e);
            return false;
        }
    }

    private static Transcript initializeTranscript(byte[] nonce) {
        Transcript transcript = new Transcript();
        transcript.append("zkdoc-proof-version", PROTOCOL_VERSION);
        transcript.append("verifier-nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        return transcript;
    }
}

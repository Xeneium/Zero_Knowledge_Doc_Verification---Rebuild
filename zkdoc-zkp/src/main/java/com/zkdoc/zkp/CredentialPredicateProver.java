package com.zkdoc.zkp;

import com.weavechain.ec.ECPoint;
import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.Allocated;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.Commitment;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Proof;
import com.weavechain.zk.bulletproofs.Prover;
import com.weavechain.zk.bulletproofs.Transcript;
import com.weavechain.zk.bulletproofs.Utils;
import com.weavechain.zk.bulletproofs.gadgets.NumberIsGreaterOrEqual;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Issuer/holder-side proof construction. This class is deliberately not an HTTP endpoint. */
public class CredentialPredicateProver {
    public static final String PROOF_VERSION = "zkdoc-range-v1";
    private static final int BIT_SIZE = 64;
    private final PedersenCommitment pedersenCommitment;
    private final BulletProofGenerators generators;
    private final NumberIsGreaterOrEqual predicate = new NumberIsGreaterOrEqual();

    public CredentialPredicateProver(PedersenCommitment pedersenCommitment, BulletProofGenerators generators) {
        this.pedersenCommitment = pedersenCommitment;
        this.generators = generators;
    }

    public byte[] prove(long value, Scalar blinding, long threshold, byte[] nonce) {
        if (value < threshold || threshold < 0 || nonce == null || nonce.length == 0) {
            throw new IllegalArgumentException("A proof requires value >= non-negative threshold and a nonce");
        }
        Transcript transcript = transcriptFor(nonce);
        Prover prover = new Prover(transcript, pedersenCommitment);
        List<ECPoint> commitments = new ArrayList<>();

        Commitment valueCommitment = prover.commit(Utils.scalar(value), blinding);
        Allocated valueAllocated = new Allocated(valueCommitment.getVariable(), BigInteger.valueOf(value));
        commitments.add(valueCommitment.getCommitment());

        BigInteger difference = BigInteger.valueOf(value).subtract(BigInteger.valueOf(threshold));
        Commitment differenceCommitment = prover.commit(Utils.scalar(difference), Utils.randomScalar());
        Allocated differenceAllocated = new Allocated(differenceCommitment.getVariable(), difference);
        commitments.add(differenceCommitment.getCommitment());

        if (!predicate.checkGreater(prover, valueAllocated, differenceAllocated, BigInteger.valueOf(threshold), BIT_SIZE)) {
            throw new IllegalArgumentException("Predicate constraints could not be satisfied");
        }
        try {
            return new Proof(prover.prove(generators), commitments).serialize();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize range proof", exception);
        }
    }

    static Transcript transcriptFor(byte[] nonce) {
        Transcript transcript = new Transcript();
        transcript.append("zkdoc-proof-version", PROOF_VERSION);
        transcript.append("verifier-nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        return transcript;
    }
}

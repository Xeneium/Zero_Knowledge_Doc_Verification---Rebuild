package com.zkdoc.zkp;

import com.weavechain.ec.ECPoint;
import com.weavechain.zk.bulletproofs.Allocated;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.BulletProofs;
import com.weavechain.zk.bulletproofs.Proof;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Transcript;
import com.weavechain.zk.bulletproofs.Verifier;
import com.weavechain.zk.bulletproofs.Variable;
import com.weavechain.zk.bulletproofs.gadgets.NumberIsGreaterOrEqual;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialTrustVerifier;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.RegisteredKey;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CredentialPredicateVerifier {
    private static final int BIT_SIZE = 64;
    private final CommitmentStore commitmentStore;
    private final CanonicalCredentialEncoder canonicalEncoder;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner issuerSigner;
    private final CredentialTrustVerifier credentialTrustVerifier;
    private final KeyRegistryService keyRegistryService;
    private final PedersenCommitment pedersenCommitment;
    private final BulletProofGenerators generators;
    private final NumberIsGreaterOrEqual predicate = new NumberIsGreaterOrEqual();

    @Autowired
    public CredentialPredicateVerifier(
            CommitmentStore commitmentStore,
            CanonicalCredentialEncoder canonicalEncoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner issuerSigner,
            CredentialTrustVerifier credentialTrustVerifier,
            @Autowired(required = false) KeyRegistryService keyRegistryService) {
        this.commitmentStore = commitmentStore;
        this.canonicalEncoder = canonicalEncoder;
        this.hasher = hasher;
        this.issuerSigner = issuerSigner;
        this.credentialTrustVerifier = credentialTrustVerifier;
        this.keyRegistryService = keyRegistryService;
        try {
            this.pedersenCommitment = PedersenCommitment.getDefault();
            this.generators = new BulletProofGenerators(128, 1);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to initialize predicate verifier", exception);
        }
    }

    public CredentialPredicateVerifier(
            CommitmentStore commitmentStore,
            CanonicalCredentialEncoder canonicalEncoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner issuerSigner,
            CredentialTrustVerifier credentialTrustVerifier) {
        this(commitmentStore, canonicalEncoder, hasher, issuerSigner, credentialTrustVerifier, null);
    }

    public CredentialPredicateVerificationResult verify(
            String proofVersion, UUID credentialId, String attributeName, long threshold, byte[] nonce, byte[] proofBytes) {
        if (!CredentialPredicateProver.PROOF_VERSION.equals(proofVersion)
                || threshold < 0 || nonce == null || nonce.length == 0 || proofBytes == null) {
            return new CredentialPredicateVerificationResult(false);
        }
        if (!credentialTrustVerifier.isActiveAndValid(credentialId)) {
            return new CredentialPredicateVerificationResult(false);
        }
        AttributeCommitment stored = commitmentStore.findLatestByCredentialIdAndAttributeName(credentialId, attributeName)
                .orElse(null);
        if (stored == null || !hasValidIssuerAttestation(stored)) {
            return new CredentialPredicateVerificationResult(false);
        }
        try {
            Proof proof = Proof.deserialize(proofBytes);
            if (proof.getCommitments().size() != 2
                    || !MessageDigest.isEqual(proof.getCommitment(0).toByteArray(), stored.commitment())) {
                return new CredentialPredicateVerificationResult(false);
            }

            Transcript transcript = CredentialPredicateProver.transcriptFor(nonce);
            Verifier verifier = new Verifier(transcript);
            ECPoint storedCommitment = BulletProofs.getFactory().fromCompressed(stored.commitment());
            Variable valueVariable = verifier.commit(storedCommitment);
            Variable differenceVariable = verifier.commit(proof.getCommitment(1));
            if (!predicate.checkGreater(
                    verifier,
                    new Allocated(valueVariable, null),
                    new Allocated(differenceVariable, null),
                    BigInteger.valueOf(threshold),
                    BIT_SIZE)) {
                return new CredentialPredicateVerificationResult(false);
            }
            return new CredentialPredicateVerificationResult(verifier.verify(proof, pedersenCommitment, generators));
        } catch (IOException | RuntimeException exception) {
            return new CredentialPredicateVerificationResult(false);
        }
    }

    private boolean hasValidIssuerAttestation(AttributeCommitment commitment) {
        byte[] expectedPayload = canonicalEncoder.encodeCommitmentAttestation(
                commitment.credentialId(), commitment.attributeName(), commitment.commitment());
        if (!Arrays.equals(expectedPayload, commitment.attestationPayload())) {
            return false;
        }

        byte[] digest = hasher.hash(expectedPayload);

        // First attempt verification against KeyRegistryService if present
        if (keyRegistryService != null) {
            if (commitment.attestationKeyId() != null) {
                if (keyRegistryService.verify(
                        commitment.attestationKeyId(),
                        KeyPurpose.COMMITMENT_ATTESTATION_SIGNING,
                        null,
                        digest,
                        commitment.attestation())) {
                    return true;
                }
            }
        }

        // Fallback to issuerSigner
        if (issuerSigner != null) {
            return issuerSigner.verify(digest, commitment.attestation());
        }

        return false;
    }
}

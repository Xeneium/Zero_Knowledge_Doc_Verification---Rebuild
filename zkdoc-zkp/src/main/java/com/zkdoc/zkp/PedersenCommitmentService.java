package com.zkdoc.zkp;

import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofs;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PedersenCommitmentService {
    private static final int MAX_BLINDING_ATTEMPTS = 10;
    private final CommitmentStore commitmentStore;
    private final CanonicalCredentialEncoder canonicalEncoder;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner issuerSigner;
    private final BlindingFactorSource blindingFactorSource;
    private final PedersenCommitment pedersenCommitment;

    public PedersenCommitmentService(
            CommitmentStore commitmentStore,
            CanonicalCredentialEncoder canonicalEncoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner issuerSigner,
            BlindingFactorSource blindingFactorSource) {
        this.commitmentStore = commitmentStore;
        this.canonicalEncoder = canonicalEncoder;
        this.hasher = hasher;
        this.issuerSigner = issuerSigner;
        this.blindingFactorSource = blindingFactorSource;
        try {
            this.pedersenCommitment = PedersenCommitment.getDefault();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to initialize Pedersen commitment generators", exception);
        }
    }

    /** Creates C = vG + rH for an integer-valued credential attribute. */
    public AttributeCommitment commit(UUID credentialId, String attributeName, long attributeValue) {
        Scalar value = com.weavechain.zk.bulletproofs.Utils.scalar(attributeValue);
        for (int attempt = 0; attempt < MAX_BLINDING_ATTEMPTS; attempt++) {
            Scalar blinding = freshBlinding();
            byte[] blindingFingerprint = sha256(blinding.toByteArray());
            if (commitmentStore.blindingFingerprintExists(blindingFingerprint)) {
                continue;
            }

            byte[] commitment = pedersenCommitment.commit(value, blinding).toByteArray();
            byte[] attestationPayload = canonicalEncoder.encodeCommitmentAttestation(
                    credentialId, attributeName, commitment);
            byte[] attestation = issuerSigner.sign(hasher.hash(attestationPayload));
            AttributeCommitment result = new AttributeCommitment(
                    UUID.randomUUID(), credentialId, attributeName, commitment, attestation,
                    attestationPayload, blindingFingerprint);
            try {
                commitmentStore.save(result);
                return result;
            } catch (DataIntegrityViolationException exception) {
                // A concurrent issuer won the same fingerprint; discard r and retry with fresh randomness.
            }
        }
        throw new IllegalStateException("Unable to create a unique Pedersen blinding factor");
    }

    private Scalar freshBlinding() {
        byte[] randomBytes = new byte[32];
        do {
            blindingFactorSource.nextBytes(randomBytes);
        } while (Arrays.equals(randomBytes, new byte[32]));
        return BulletProofs.getFactory().fromBits(randomBytes);
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

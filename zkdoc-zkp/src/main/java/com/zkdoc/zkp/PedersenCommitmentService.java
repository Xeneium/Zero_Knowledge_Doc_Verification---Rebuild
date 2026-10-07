package com.zkdoc.zkp;

import com.weavechain.ec.ECPoint;
import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofs;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.RegisteredKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Production-grade Pedersen commitment service for verifiable credential attributes.
 * <p>
 * Evaluates C = vG + rH over Curve25519 / Ristretto group elements:
 * - v: integer-valued attribute
 * - r: 256-bit cryptographically secure blinding scalar
 * - G, H: orthogonal generators with unknown discrete logarithm relationship
 * <p>
 * Issuer attestation is signed under {@link KeyPurpose#COMMITMENT_ATTESTATION_SIGNING}
 * (or active registered issuer key) through the KeyRegistryService trust boundary.
 */
@Service
public class PedersenCommitmentService {

    private static final Logger log = LoggerFactory.getLogger(PedersenCommitmentService.class);
    private static final int MAX_BLINDING_ATTEMPTS = 10;

    private final CommitmentStore commitmentStore;
    private final CanonicalCredentialEncoder canonicalEncoder;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner fallbackSigner;
    private final BlindingFactorSource blindingFactorSource;
    private final PedersenCommitment pedersenCommitment;
    private final KeyRegistryService keyRegistryService;

    @Autowired
    public PedersenCommitmentService(
            CommitmentStore commitmentStore,
            CanonicalCredentialEncoder canonicalEncoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner fallbackSigner,
            BlindingFactorSource blindingFactorSource,
            @Autowired(required = false) KeyRegistryService keyRegistryService) {
        this.commitmentStore = Objects.requireNonNull(commitmentStore, "commitmentStore must not be null");
        this.canonicalEncoder = Objects.requireNonNull(canonicalEncoder, "canonicalEncoder must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.fallbackSigner = fallbackSigner;
        this.blindingFactorSource = Objects.requireNonNull(blindingFactorSource, "blindingFactorSource must not be null");
        this.keyRegistryService = keyRegistryService;
        try {
            this.pedersenCommitment = PedersenCommitment.getDefault();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to initialize Pedersen commitment generators", exception);
        }
    }

    public PedersenCommitmentService(
            CommitmentStore commitmentStore,
            CanonicalCredentialEncoder canonicalEncoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner fallbackSigner,
            BlindingFactorSource blindingFactorSource) {
        this(commitmentStore, canonicalEncoder, hasher, fallbackSigner, blindingFactorSource, null);
    }

    /**
     * Creates an authenticated Pedersen commitment C = vG + rH and returns both the public record
     * and the secret holder opening material (AttributeOpeningSecret).
     *
     * @param credentialId The bound credential UUID
     * @param attributeName The attribute name
     * @param attributeValue The integer value being committed
     * @param issuerOwnerId Optional issuer identifier for key registry resolution
     * @return AttributeCommitmentResult containing public commitment and holder opening secret
     */
    public AttributeCommitmentResult commitWithOpening(
            UUID credentialId, String attributeName, long attributeValue, String issuerOwnerId) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");

        Scalar value = com.weavechain.zk.bulletproofs.Utils.scalar(attributeValue);
        for (int attempt = 0; attempt < MAX_BLINDING_ATTEMPTS; attempt++) {
            Scalar blinding = freshBlinding();
            byte[] blindingBytes = blinding.toByteArray();
            byte[] blindingFingerprint = sha256(blindingBytes);
            if (commitmentStore.blindingFingerprintExists(blindingFingerprint)) {
                continue;
            }

            byte[] commitment = pedersenCommitment.commit(value, blinding).toByteArray();
            byte[] attestationPayload = canonicalEncoder.encodeCommitmentAttestation(
                    credentialId, attributeName, commitment);
            byte[] payloadDigest = hasher.hash(attestationPayload);

            // Compute issuer attestation signature via KeyRegistry or fallback
            SigningResult signingResult = signAttestation(payloadDigest, issuerOwnerId);

            AttributeCommitment result = new AttributeCommitment(
                    UUID.randomUUID(),
                    credentialId,
                    attributeName,
                    commitment,
                    signingResult.signature(),
                    attestationPayload,
                    blindingFingerprint,
                    signingResult.keyId());

            try {
                commitmentStore.save(result);
                AttributeOpeningSecret secret = new AttributeOpeningSecret(
                        credentialId, attributeName, attributeValue, blindingBytes, blindingFingerprint);
                return new AttributeCommitmentResult(result, secret);
            } catch (DataIntegrityViolationException exception) {
                // A concurrent transaction claimed the same blinding fingerprint; retry with fresh scalar
                log.warn("Blinding fingerprint collision detected, retrying attempt {}", attempt + 1);
            }
        }
        throw new IllegalStateException("Unable to create a unique Pedersen blinding factor after maximum attempts");
    }

    /**
     * Legacy commit method that creates C = vG + rH and returns the public AttributeCommitment record.
     */
    public AttributeCommitment commit(UUID credentialId, String attributeName, long attributeValue) {
        return commitWithOpening(credentialId, attributeName, attributeValue, null).commitment();
    }

    /**
     * Verifies that a Pedersen commitment C opens to a purported value v under blinding scalar r.
     * C ?= vG + rH
     */
    public boolean verifyOpening(long purportedValue, byte[] blindingBytes, byte[] commitmentBytes) {
        if (blindingBytes == null || commitmentBytes == null || blindingBytes.length == 0 || commitmentBytes.length == 0) {
            return false;
        }
        try {
            Scalar value = com.weavechain.zk.bulletproofs.Utils.scalar(purportedValue);
            Scalar blinding = BulletProofs.getFactory().fromBits(blindingBytes);
            byte[] computed = pedersenCommitment.commit(value, blinding).toByteArray();
            return MessageDigest.isEqual(computed, commitmentBytes);
        } catch (Exception e) {
            log.debug("Verification of commitment opening failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the cryptographic attestation signature on an AttributeCommitment record.
     */
    public boolean verifyAttestation(AttributeCommitment commitment, String expectedIssuerOwnerId) {
        if (commitment == null) {
            return false;
        }
        byte[] expectedPayload = canonicalEncoder.encodeCommitmentAttestation(
                commitment.credentialId(), commitment.attributeName(), commitment.commitment());
        if (!Arrays.equals(expectedPayload, commitment.attestationPayload())) {
            return false;
        }
        byte[] digest = hasher.hash(expectedPayload);

        if (keyRegistryService != null) {
            // If commitment has a specific attestationKeyId recorded
            if (commitment.attestationKeyId() != null) {
                if (keyRegistryService.verify(
                        commitment.attestationKeyId(),
                        KeyPurpose.COMMITMENT_ATTESTATION_SIGNING,
                        expectedIssuerOwnerId,
                        digest,
                        commitment.attestation())) {
                    return true;
                }
            }

            // Search active/retired attestation keys for the issuer
            if (expectedIssuerOwnerId != null) {
                var keys = keyRegistryService.keysForVerificationByOwner(
                        KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, expectedIssuerOwnerId);
                for (RegisteredKey key : keys) {
                    if (keyRegistryService.verify(
                            key.keyId(),
                            KeyPurpose.COMMITMENT_ATTESTATION_SIGNING,
                            expectedIssuerOwnerId,
                            digest,
                            commitment.attestation())) {
                        return true;
                    }
                }
            }
        }

        // Fallback signer check
        if (fallbackSigner != null) {
            return fallbackSigner.verify(digest, commitment.attestation());
        }

        return false;
    }

    private SigningResult signAttestation(byte[] digest, String issuerOwnerId) {
        if (keyRegistryService != null) {
            // 1. Try finding an active COMMITMENT_ATTESTATION_SIGNING key
            Optional<RegisteredKey> attestationKey = (issuerOwnerId != null)
                    ? keyRegistryService.activeSigningKey(KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, issuerOwnerId)
                    : keyRegistryService.activeSigningKey(KeyPurpose.COMMITMENT_ATTESTATION_SIGNING);

            if (attestationKey.isPresent()) {
                String keyId = attestationKey.get().keyId();
                try {
                    byte[] sig = keyRegistryService.sign(keyId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, issuerOwnerId, digest);
                    return new SigningResult(sig, keyId);
                } catch (Exception e) {
                    log.warn("Failed signing attestation with keyId {}: {}", keyId, e.getMessage());
                }
            }
        }

        if (fallbackSigner != null) {
            return new SigningResult(fallbackSigner.sign(digest), fallbackSigner.getKeyId());
        }

        throw new IllegalStateException("No signing key available for attribute commitment attestation");
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

    private record SigningResult(byte[] signature, String keyId) {}
}

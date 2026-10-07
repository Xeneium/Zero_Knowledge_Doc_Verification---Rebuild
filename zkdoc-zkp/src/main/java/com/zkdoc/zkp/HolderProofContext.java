package com.zkdoc.zkp;

import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.HolderCredentialId;
import java.util.Arrays;
import java.util.Objects;
import javax.security.auth.Destroyable;

/**
 * Immutable proving context for holder-side zero-knowledge proofs.
 * <p>
 * Contains both public verification anchors (credential digest, attribute commitment,
 * issuer attestation signature) and private witness material (attribute value, blinding factor).
 * <p>
 * Security invariants:
 * <ul>
 *   <li>The private witness material (blindingFactor, attributeValue) is explicitly redacted
 *       in {@link #toString()} to prevent accidental logging or telemetry leaks.</li>
 *   <li>Implements {@link Destroyable} allowing zeroization of in-memory sensitive witness arrays.</li>
 *   <li>Never exposed directly across public REST boundaries or verifier interfaces.</li>
 * </ul>
 */
public record HolderProofContext(
        HolderCredentialId holderCredentialId,
        CredentialId credentialId,
        byte[] credentialDigest,
        String attributeName,
        byte[] commitmentPoint,
        byte[] attestationSignature,
        String attestationKeyId,
        long attributeValue,
        byte[] blindingFactor,
        byte[] blindingFingerprint,
        String protocolVersion
) implements Destroyable {

    public HolderProofContext {
        Objects.requireNonNull(holderCredentialId, "holderCredentialId must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(credentialDigest, "credentialDigest must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        Objects.requireNonNull(commitmentPoint, "commitmentPoint must not be null");
        Objects.requireNonNull(attestationSignature, "attestationSignature must not be null");
        Objects.requireNonNull(blindingFactor, "blindingFactor must not be null");
        Objects.requireNonNull(blindingFingerprint, "blindingFingerprint must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");

        credentialDigest = credentialDigest.clone();
        commitmentPoint = commitmentPoint.clone();
        attestationSignature = attestationSignature.clone();
        blindingFactor = blindingFactor.clone();
        blindingFingerprint = blindingFingerprint.clone();
    }

    @Override
    public byte[] credentialDigest() {
        return credentialDigest.clone();
    }

    @Override
    public byte[] commitmentPoint() {
        return commitmentPoint.clone();
    }

    @Override
    public byte[] attestationSignature() {
        return attestationSignature.clone();
    }

    @Override
    public byte[] blindingFactor() {
        return blindingFactor.clone();
    }

    @Override
    public byte[] blindingFingerprint() {
        return blindingFingerprint.clone();
    }

    /**
     * Erases sensitive blinding factor bytes from memory upon destruction.
     */
    @Override
    public void destroy() {
        Arrays.fill(blindingFactor, (byte) 0);
    }

    @Override
    public boolean isDestroyed() {
        for (byte b : blindingFactor) {
            if (b != 0) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "HolderProofContext{" +
                "holderCredentialId=" + holderCredentialId +
                ", credentialId=" + credentialId +
                ", attributeName='" + attributeName + '\'' +
                ", attestationKeyId='" + attestationKeyId + '\'' +
                ", protocolVersion='" + protocolVersion + '\'' +
                ", attributeValue=[REDACTED]" +
                ", blindingFactor=[REDACTED]" +
                '}';
    }
}

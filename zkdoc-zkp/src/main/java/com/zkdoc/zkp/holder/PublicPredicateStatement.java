package com.zkdoc.zkp.holder;

import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.zkp.engine.AttributePredicate;
import java.util.Arrays;
import java.util.Objects;

/**
 * Public statement associated with a generated zero-knowledge predicate proof.
 * <p>
 * Contains all public anchors and statement parameters required for cryptographic verification:
 * - Credential ID & authoritative digest
 * - Attribute name & predicate specification (operator, threshold)
 * - Issuer-attested commitment point
 * - Attestation signature & key identifier
 * - Challenge/transcript nonce
 * <p>
 * ABSOLUTELY NO PRIVATE WITNESS (attribute value, blinder) is present here.
 */
public record PublicPredicateStatement(
        HolderCredentialId holderCredentialId,
        CredentialId credentialId,
        byte[] credentialDigest,
        AttributePredicate predicate,
        byte[] commitmentPoint,
        byte[] attestationSignature,
        String attestationKeyId,
        byte[] transcriptNonce
) {
    public PublicPredicateStatement {
        Objects.requireNonNull(holderCredentialId, "holderCredentialId must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(credentialDigest, "credentialDigest must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(commitmentPoint, "commitmentPoint must not be null");
        Objects.requireNonNull(attestationSignature, "attestationSignature must not be null");
        Objects.requireNonNull(transcriptNonce, "transcriptNonce must not be null");

        credentialDigest = credentialDigest.clone();
        commitmentPoint = commitmentPoint.clone();
        attestationSignature = attestationSignature.clone();
        transcriptNonce = transcriptNonce.clone();
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
    public byte[] transcriptNonce() {
        return transcriptNonce.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PublicPredicateStatement that)) return false;
        return holderCredentialId.equals(that.holderCredentialId) &&
                credentialId.equals(that.credentialId) &&
                Arrays.equals(credentialDigest, that.credentialDigest) &&
                predicate.equals(that.predicate) &&
                Arrays.equals(commitmentPoint, that.commitmentPoint) &&
                Arrays.equals(attestationSignature, that.attestationSignature) &&
                Objects.equals(attestationKeyId, that.attestationKeyId) &&
                Arrays.equals(transcriptNonce, that.transcriptNonce);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(holderCredentialId, credentialId, predicate, attestationKeyId);
        result = 31 * result + Arrays.hashCode(credentialDigest);
        result = 31 * result + Arrays.hashCode(commitmentPoint);
        result = 31 * result + Arrays.hashCode(attestationSignature);
        result = 31 * result + Arrays.hashCode(transcriptNonce);
        return result;
    }

    @Override
    public String toString() {
        return "PublicPredicateStatement{" +
                "holderCredentialId=" + holderCredentialId +
                ", credentialId=" + credentialId +
                ", predicate=" + predicate +
                ", attestationKeyId='" + attestationKeyId + '\'' +
                '}';
    }
}

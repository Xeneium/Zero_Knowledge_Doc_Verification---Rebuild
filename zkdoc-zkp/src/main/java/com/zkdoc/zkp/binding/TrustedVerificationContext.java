package com.zkdoc.zkp.binding;

import com.zkdoc.credential.CredentialId;
import com.zkdoc.zkp.engine.AttributePredicate;
import java.util.Arrays;
import java.util.Objects;

/**
 * Authoritative, server-resolved verification context constructed entirely from trusted state.
 * <p>
 * Contains all verified public anchors:
 * - Credential ID & authoritative digest
 * - Authoritative attribute definition and predicate
 * - Authenticated commitment point & attestation signature
 * - Attestation key ID & issuer ID
 * - Freshness challenge / verifier nonce
 * <p>
 * ABSOLUTELY NO CALLER-SUPPLIED CRYPTOGRAPHIC VALUES:
 * The commitment, digest, and key IDs are derived solely by the server.
 */
public record TrustedVerificationContext(
        CredentialId credentialId,
        byte[] authoritativeCredentialDigest,
        AttributePredicate predicate,
        byte[] authenticatedCommitmentPoint,
        byte[] attestationSignature,
        String attestationKeyId,
        String issuerId,
        byte[] verifierNonce,
        String protocolVersion
) {
    public TrustedVerificationContext {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(authoritativeCredentialDigest, "authoritativeCredentialDigest must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(authenticatedCommitmentPoint, "authenticatedCommitmentPoint must not be null");
        Objects.requireNonNull(attestationSignature, "attestationSignature must not be null");
        Objects.requireNonNull(verifierNonce, "verifierNonce must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");

        authoritativeCredentialDigest = authoritativeCredentialDigest.clone();
        authenticatedCommitmentPoint = authenticatedCommitmentPoint.clone();
        attestationSignature = attestationSignature.clone();
        verifierNonce = verifierNonce.clone();
    }

    @Override
    public byte[] authoritativeCredentialDigest() {
        return authoritativeCredentialDigest.clone();
    }

    @Override
    public byte[] authenticatedCommitmentPoint() {
        return authenticatedCommitmentPoint.clone();
    }

    @Override
    public byte[] attestationSignature() {
        return attestationSignature.clone();
    }

    @Override
    public byte[] verifierNonce() {
        return verifierNonce.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustedVerificationContext that)) return false;
        return credentialId.equals(that.credentialId) &&
                Arrays.equals(authoritativeCredentialDigest, that.authoritativeCredentialDigest) &&
                predicate.equals(that.predicate) &&
                Arrays.equals(authenticatedCommitmentPoint, that.authenticatedCommitmentPoint) &&
                Arrays.equals(attestationSignature, that.attestationSignature) &&
                Objects.equals(attestationKeyId, that.attestationKeyId) &&
                Objects.equals(issuerId, that.issuerId) &&
                Arrays.equals(verifierNonce, that.verifierNonce) &&
                protocolVersion.equals(that.protocolVersion);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(credentialId, predicate, attestationKeyId, issuerId, protocolVersion);
        result = 31 * result + Arrays.hashCode(authoritativeCredentialDigest);
        result = 31 * result + Arrays.hashCode(authenticatedCommitmentPoint);
        result = 31 * result + Arrays.hashCode(attestationSignature);
        result = 31 * result + Arrays.hashCode(verifierNonce);
        return result;
    }

    @Override
    public String toString() {
        return "TrustedVerificationContext{" +
                "credentialId=" + credentialId +
                ", predicate=" + predicate +
                ", attestationKeyId='" + attestationKeyId + '\'' +
                ", issuerId='" + issuerId + '\'' +
                ", protocolVersion='" + protocolVersion + '\'' +
                '}';
    }
}

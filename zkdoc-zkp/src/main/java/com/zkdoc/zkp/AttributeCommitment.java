package com.zkdoc.zkp;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Public, authenticated Pedersen commitment record.
 * Contains no raw attribute value or raw blinding factor secret.
 */
public record AttributeCommitment(
        UUID id,
        UUID credentialId,
        String attributeName,
        byte[] commitment,
        byte[] attestation,
        byte[] attestationPayload,
        byte[] blindingFingerprint,
        String attestationKeyId) {

    public AttributeCommitment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        Objects.requireNonNull(commitment, "commitment must not be null");
        Objects.requireNonNull(attestation, "attestation must not be null");
        Objects.requireNonNull(attestationPayload, "attestationPayload must not be null");
        Objects.requireNonNull(blindingFingerprint, "blindingFingerprint must not be null");

        commitment = commitment.clone();
        attestation = attestation.clone();
        attestationPayload = attestationPayload.clone();
        blindingFingerprint = blindingFingerprint.clone();
    }

    /**
     * Backward-compatible 7-argument constructor for legacy calls.
     */
    public AttributeCommitment(
            UUID id,
            UUID credentialId,
            String attributeName,
            byte[] commitment,
            byte[] attestation,
            byte[] attestationPayload,
            byte[] blindingFingerprint) {
        this(id, credentialId, attributeName, commitment, attestation, attestationPayload, blindingFingerprint, null);
    }

    @Override
    public byte[] commitment() {
        return commitment.clone();
    }

    @Override
    public byte[] attestation() {
        return attestation.clone();
    }

    @Override
    public byte[] attestationPayload() {
        return attestationPayload.clone();
    }

    @Override
    public byte[] blindingFingerprint() {
        return blindingFingerprint.clone();
    }
}

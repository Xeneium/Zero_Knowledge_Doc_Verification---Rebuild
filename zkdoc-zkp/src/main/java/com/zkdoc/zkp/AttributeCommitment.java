package com.zkdoc.zkp;

import java.util.UUID;

/** Contains no raw attribute value or blinding factor. */
public record AttributeCommitment(
        UUID id,
        UUID credentialId,
        String attributeName,
        byte[] commitment,
        byte[] attestation,
        byte[] attestationPayload,
        byte[] blindingFingerprint) {
}

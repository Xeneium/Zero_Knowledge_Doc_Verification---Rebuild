package com.zkdoc.credential;

import java.util.UUID;
import java.time.Instant;

public record StoredCredential(
        UUID credentialId,
        Instant expiresAt,
        CredentialStatus status,
        byte[] canonicalPayload,
        byte[] digest,
        byte[] signature) {
}

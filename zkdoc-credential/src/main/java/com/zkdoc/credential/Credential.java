package com.zkdoc.credential;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Credential(
        String issuerId,
        UUID credentialId,
        List<CredentialAttribute> attributes,
        Instant issuedAt,
        Instant expiresAt,
        CredentialStatus status) {
    public Credential {
        Objects.requireNonNull(issuerId, "issuerId must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}

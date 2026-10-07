package com.zkdoc.credential;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative, immutable domain representation of a Verifiable Credential.
 * Holds canonical claim data and cryptographic identity references.
 * Operational status transitions return new records without modifying underlying claims.
 */
public record Credential(
        CredentialId id,
        String schemaId,
        IssuerId issuerId,
        KeyId issuerKeyId,
        CredentialVersion version,
        Instant issuedAt,
        Instant expiresAt,
        CredentialStatus status,
        List<CredentialAttribute> attributes,
        CanonicalizationVersion canonicalizationVersion) {

    public Credential {
        Objects.requireNonNull(id, "Credential ID must not be null");
        Objects.requireNonNull(schemaId, "Schema ID must not be null");
        Objects.requireNonNull(issuerId, "Issuer ID must not be null");
        Objects.requireNonNull(issuerKeyId, "Issuer Key ID must not be null");
        Objects.requireNonNull(version, "Credential version must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        Objects.requireNonNull(canonicalizationVersion, "canonicalizationVersion must not be null");

        if (expiresAt.isBefore(issuedAt) || expiresAt.equals(issuedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt (" + expiresAt + ") must be strictly after issuedAt (" + issuedAt + ")");
        }

        // Prevent duplicate attribute names
        Set<String> seenNames = new HashSet<>();
        for (CredentialAttribute attr : attributes) {
            if (!seenNames.add(attr.name())) {
                throw new IllegalArgumentException("Duplicate attribute name rejected: " + attr.name());
            }
        }
        attributes = List.copyOf(attributes);
    }

    /**
     * Backward-compatible constructor for existing tests and services.
     */
    public Credential(
            String issuerId,
            UUID credentialId,
            List<CredentialAttribute> attributes,
            Instant issuedAt,
            Instant expiresAt,
            CredentialStatus status) {
        this(
                CredentialId.of(credentialId),
                "urn:zkdoc:schema:default:v1",
                IssuerId.of(issuerId),
                KeyId.of("default-issuer-key-1"),
                CredentialVersion.V1,
                issuedAt,
                expiresAt,
                status,
                attributes,
                CanonicalizationVersion.V1);
    }

    /**
     * Backward-compatible accessor for UUID credential ID.
     */
    public UUID credentialId() {
        return id.value();
    }

    /**
     * Backward-compatible accessor for String issuer ID.
     */
    public String issuerIdString() {
        return issuerId.value();
    }

    /**
     * Executes an operational lifecycle state transition, enforcing state machine invariants.
     */
    public Credential transitionTo(CredentialStatus nextStatus) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("Illegal status transition from " + status + " to " + nextStatus);
        }
        return new Credential(
                id,
                schemaId,
                issuerId,
                issuerKeyId,
                version,
                issuedAt,
                expiresAt,
                nextStatus,
                attributes,
                canonicalizationVersion);
    }

    /**
     * Verifies if this credential is valid at a given point in time.
     */
    public boolean isValidAt(Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        return status == CredentialStatus.ACTIVE
                && !timestamp.isBefore(issuedAt)
                && timestamp.isBefore(expiresAt);
    }
}

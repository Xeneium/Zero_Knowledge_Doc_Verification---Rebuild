package com.zkdoc.credential;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Step-by-step builder and draft representation for safely constructing validated Credentials.
 * Ensures partially initialized states cannot accidentally enter the cryptographic pipeline.
 */
public final class CredentialDraft {
    private CredentialId id = CredentialId.random();
    private String schemaId = "urn:zkdoc:schema:default:v1";
    private IssuerId issuerId;
    private KeyId issuerKeyId;
    private CredentialVersion version = CredentialVersion.V1;
    private Instant issuedAt = Instant.now();
    private Instant expiresAt;
    private CredentialStatus status = CredentialStatus.ACTIVE;
    private final List<CredentialAttribute> attributes = new ArrayList<>();
    private CanonicalizationVersion canonicalizationVersion = CanonicalizationVersion.V1;

    public static CredentialDraft newDraft() {
        return new CredentialDraft();
    }

    public CredentialDraft id(CredentialId id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        return this;
    }

    public CredentialDraft id(UUID uuid) {
        this.id = CredentialId.of(uuid);
        return this;
    }

    public CredentialDraft schemaId(String schemaId) {
        this.schemaId = Objects.requireNonNull(schemaId, "schemaId must not be null");
        return this;
    }

    public CredentialDraft issuerId(IssuerId issuerId) {
        this.issuerId = Objects.requireNonNull(issuerId, "issuerId must not be null");
        return this;
    }

    public CredentialDraft issuerId(String issuerId) {
        this.issuerId = IssuerId.of(issuerId);
        return this;
    }

    public CredentialDraft issuerKeyId(KeyId issuerKeyId) {
        this.issuerKeyId = Objects.requireNonNull(issuerKeyId, "issuerKeyId must not be null");
        return this;
    }

    public CredentialDraft issuerKeyId(String keyId) {
        this.issuerKeyId = KeyId.of(keyId);
        return this;
    }

    public CredentialDraft version(CredentialVersion version) {
        this.version = Objects.requireNonNull(version, "version must not be null");
        return this;
    }

    public CredentialDraft issuedAt(Instant issuedAt) {
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        return this;
    }

    public CredentialDraft expiresAt(Instant expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return this;
    }

    public CredentialDraft status(CredentialStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        return this;
    }

    public CredentialDraft attribute(CredentialAttribute attribute) {
        this.attributes.add(Objects.requireNonNull(attribute, "attribute must not be null"));
        return this;
    }

    public CredentialDraft attribute(String name, String value) {
        this.attributes.add(new CredentialAttribute(name, value));
        return this;
    }

    public CredentialDraft attribute(String name, long integerValue) {
        this.attributes.add(new CredentialAttribute(name, integerValue));
        return this;
    }

    public CredentialDraft attribute(String name, boolean booleanValue) {
        this.attributes.add(new CredentialAttribute(name, booleanValue));
        return this;
    }

    public CredentialDraft canonicalizationVersion(CanonicalizationVersion canonicalizationVersion) {
        this.canonicalizationVersion = Objects.requireNonNull(canonicalizationVersion, "canonicalizationVersion must not be null");
        return this;
    }

    public Credential build() {
        if (issuerId == null) {
            throw new IllegalStateException("issuerId is required to build Credential");
        }
        if (issuerKeyId == null) {
            issuerKeyId = KeyId.of("default-key-1");
        }
        if (expiresAt == null) {
            expiresAt = issuedAt.plusSeconds(365L * 24 * 3600);
        }
        return new Credential(
                id,
                schemaId,
                issuerId,
                issuerKeyId,
                version,
                issuedAt,
                expiresAt,
                status,
                attributes,
                canonicalizationVersion);
    }
}

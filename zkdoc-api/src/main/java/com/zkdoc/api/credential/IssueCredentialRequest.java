package com.zkdoc.api.credential;

import com.zkdoc.credential.AttributeType;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialDraft;
import com.zkdoc.credential.CredentialStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transport-level API DTO for credential issuance requests.
 * Explicitly decoupled from the authoritative internal domain Credential model.
 */
public record IssueCredentialRequest(
        String issuerId,
        UUID credentialId,
        String schemaId,
        String issuerKeyId,
        Instant issuedAt,
        Instant expiresAt,
        CredentialStatus status,
        List<AttributeRequestDto> attributes) {

    public record AttributeRequestDto(String name, String type, String value) {}

    public Credential toDomain() {
        CredentialDraft draft = CredentialDraft.newDraft()
                .issuerId(issuerId != null && !issuerId.isBlank() ? issuerId : "urn:zkdoc:issuer:default")
                .schemaId(schemaId != null && !schemaId.isBlank() ? schemaId : "urn:zkdoc:schema:default:v1")
                .issuerKeyId(issuerKeyId != null && !issuerKeyId.isBlank() ? issuerKeyId : "default-key-1");

        if (credentialId != null) {
            draft.id(credentialId);
        }
        if (issuedAt != null) {
            draft.issuedAt(issuedAt);
        }
        if (expiresAt != null) {
            draft.expiresAt(expiresAt);
        }
        if (status != null) {
            draft.status(status);
        }

        if (attributes != null) {
            for (AttributeRequestDto attr : attributes) {
                AttributeType attrType = AttributeType.STRING;
                if (attr.type() != null && !attr.type().isBlank()) {
                    try {
                        attrType = AttributeType.valueOf(attr.type().trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        attrType = AttributeType.STRING;
                    }
                }
                draft.attribute(new CredentialAttribute(attr.name(), attrType, attr.value()));
            }
        }

        return draft.build();
    }
}

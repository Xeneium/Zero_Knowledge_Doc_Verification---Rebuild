package com.zkdoc.api.credential;

import com.zkdoc.credential.AttributeType;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialDraft;
import com.zkdoc.credential.CredentialStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Transport-level API DTO for credential issuance requests.
 * Explicitly decoupled from the authoritative internal domain Credential model.
 */
public record IssueCredentialRequest(
        @NotBlank(message = "issuerId must not be blank")
        @Size(max = 256, message = "issuerId must not exceed 256 characters")
        String issuerId,

        UUID credentialId,

        @Size(max = 256, message = "schemaId must not exceed 256 characters")
        String schemaId,

        @Size(max = 128, message = "issuerKeyId must not exceed 128 characters")
        String issuerKeyId,

        Instant issuedAt,
        Instant expiresAt,
        CredentialStatus status,

        @jakarta.validation.constraints.NotEmpty(message = "attributes must not be empty")
        @Size(max = 100, message = "cannot issue credential with more than 100 attributes")
        List<@Valid AttributeRequestDto> attributes) {

    public record AttributeRequestDto(
            @NotBlank(message = "attribute name must not be blank")
            @Size(max = 128, message = "attribute name must not exceed 128 characters")
            String name,

            @Size(max = 32, message = "attribute type must not exceed 32 characters")
            String type,

            @NotNull(message = "attribute value must not be null")
            @Size(max = 4096, message = "attribute value must not exceed 4096 characters")
            String value) {}

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

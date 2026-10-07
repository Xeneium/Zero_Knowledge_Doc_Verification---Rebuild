package com.zkdoc.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CredentialDomainTest {

    @Test
    @DisplayName("Credential correctly validates invariants and supports typed attributes")
    void credentialInvariantsAndTypedAttributes() {
        CredentialId id = CredentialId.random();
        IssuerId issuerId = IssuerId.of("urn:zkdoc:issuer:transport");
        KeyId keyId = KeyId.of("key-2026");
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(3600);

        CredentialAttribute intAttr = new CredentialAttribute("age", 25L);
        assertEquals(AttributeType.INTEGER, intAttr.type());
        assertEquals(25L, intAttr.attributeValue().asInteger());
        assertTrue(intAttr.type().isCommitmentEligible());

        CredentialAttribute strAttr = new CredentialAttribute("name", "Alice");
        assertEquals(AttributeType.STRING, strAttr.type());
        assertFalse(strAttr.type().isCommitmentEligible());

        CredentialAttribute boolAttr = new CredentialAttribute("isLicensed", true);
        assertEquals(AttributeType.BOOLEAN, boolAttr.type());
        assertTrue(boolAttr.attributeValue().asBoolean());

        CredentialAttribute dateAttr = new CredentialAttribute(
                "dateOfBirth", AttributeType.DATE, "2000-05-15");
        assertEquals(AttributeType.DATE, dateAttr.type());
        assertEquals(LocalDate.of(2000, 5, 15), dateAttr.attributeValue().asDate());

        Credential credential = new Credential(
                id,
                "urn:zkdoc:schema:license:v1",
                issuerId,
                keyId,
                CredentialVersion.V1,
                issuedAt,
                expiresAt,
                CredentialStatus.ACTIVE,
                List.of(intAttr, strAttr, boolAttr, dateAttr),
                CanonicalizationVersion.V1);

        assertTrue(credential.isValidAt(issuedAt.plusSeconds(1800)));
        assertFalse(credential.isValidAt(issuedAt.minusSeconds(10)));
        assertFalse(credential.isValidAt(expiresAt.plusSeconds(10)));

        // Immutability: attributes list cannot be modified
        assertThrows(UnsupportedOperationException.class, () ->
                credential.attributes().add(new CredentialAttribute("extra", "value")));
    }

    @Test
    @DisplayName("Credential lifecycle transitions enforce state machine rules")
    void credentialLifecycleStateMachine() {
        Credential cred = CredentialDraft.newDraft()
                .issuerId("issuer-1")
                .status(CredentialStatus.DRAFT)
                .build();

        Credential active = cred.transitionTo(CredentialStatus.ACTIVE);
        assertEquals(CredentialStatus.ACTIVE, active.status());

        Credential suspended = active.transitionTo(CredentialStatus.SUSPENDED);
        assertEquals(CredentialStatus.SUSPENDED, suspended.status());

        Credential reactivated = suspended.transitionTo(CredentialStatus.ACTIVE);
        assertEquals(CredentialStatus.ACTIVE, reactivated.status());

        Credential revoked = reactivated.transitionTo(CredentialStatus.REVOKED);
        assertEquals(CredentialStatus.REVOKED, revoked.status());

        // Revocation is terminal
        assertThrows(IllegalStateException.class, () -> revoked.transitionTo(CredentialStatus.ACTIVE));
        assertThrows(IllegalStateException.class, () -> revoked.transitionTo(CredentialStatus.SUSPENDED));
    }
}

package com.zkdoc.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IssuerTest {

    @Test
    @DisplayName("Issuer validates non-blank name, valid status, and correct state transitions")
    void issuerValidationAndLifecycle() {
        IssuerId issuerId = IssuerId.of("urn:zkdoc:issuer:dept-finance");
        KeyId keyId = KeyId.of("key-rsa-2026-01");

        Issuer issuer = Issuer.create(issuerId, "Department of Finance", keyId);
        assertEquals(IssuerStatus.ACTIVE, issuer.status());
        assertTrue(issuer.canIssue());

        // Suspend
        Issuer suspended = issuer.suspend();
        assertEquals(IssuerStatus.SUSPENDED, suspended.status());
        assertFalse(suspended.canIssue());

        // Reactivate
        Issuer reactivated = suspended.activate();
        assertEquals(IssuerStatus.ACTIVE, reactivated.status());
        assertTrue(reactivated.canIssue());

        // Revoke
        Issuer revoked = reactivated.revoke();
        assertEquals(IssuerStatus.REVOKED, revoked.status());
        assertFalse(revoked.canIssue());

        // Revocation is terminal and irreversible
        assertThrows(IllegalStateException.class, revoked::activate);
        assertThrows(IllegalStateException.class, revoked::suspend);
    }

    @Test
    @DisplayName("Issuer rejects invalid IDs and blank names")
    void issuerRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> IssuerId.of(""));
        assertThrows(IllegalArgumentException.class, () -> IssuerId.of("invalid id with spaces!!"));

        assertThrows(IllegalArgumentException.class, () ->
                Issuer.create(IssuerId.of("urn:valid:id"), "   ", KeyId.of("key-1")));
    }
}

package com.zkdoc.keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyPurposeTest {

    @Test
    @DisplayName("Cryptographic key purposes are mutually exclusive and prevent purpose confusion")
    void purposeSeparationEnforced() {
        KeyPurpose jwt = KeyPurpose.JWT_SIGNING;
        KeyPurpose cred = KeyPurpose.CREDENTIAL_SIGNING;
        KeyPurpose doc = KeyPurpose.DOCUMENT_SIGNING;
        KeyPurpose commit = KeyPurpose.COMMITMENT_ATTESTATION_SIGNING;

        assertTrue(jwt.matches(KeyPurpose.JWT_SIGNING));
        assertFalse(jwt.matches(KeyPurpose.CREDENTIAL_SIGNING));
        assertFalse(jwt.matches(KeyPurpose.DOCUMENT_SIGNING));
        assertFalse(jwt.matches(KeyPurpose.COMMITMENT_ATTESTATION_SIGNING));

        assertTrue(cred.matches(KeyPurpose.CREDENTIAL_SIGNING));
        assertFalse(cred.matches(KeyPurpose.JWT_SIGNING));
        assertFalse(cred.matches(KeyPurpose.DOCUMENT_SIGNING));
        assertFalse(cred.matches(KeyPurpose.COMMITMENT_ATTESTATION_SIGNING));

        assertTrue(doc.matches(KeyPurpose.DOCUMENT_SIGNING));
        assertFalse(doc.matches(KeyPurpose.JWT_SIGNING));
        assertFalse(doc.matches(KeyPurpose.CREDENTIAL_SIGNING));
        assertFalse(doc.matches(KeyPurpose.COMMITMENT_ATTESTATION_SIGNING));

        assertTrue(commit.matches(KeyPurpose.COMMITMENT_ATTESTATION_SIGNING));
        assertFalse(commit.matches(KeyPurpose.JWT_SIGNING));
        assertFalse(commit.matches(KeyPurpose.CREDENTIAL_SIGNING));
        assertFalse(commit.matches(KeyPurpose.DOCUMENT_SIGNING));
    }
}

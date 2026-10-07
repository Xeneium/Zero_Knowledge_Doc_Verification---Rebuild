package com.zkdoc.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentSigningKeyProviderTest {

    @Test
    @DisplayName("Matching keypair succeeds consistency check and registers")
    void matchingKeypairRegisters() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        DevelopmentSigningKeyProvider provider = new DevelopmentSigningKeyProvider();
        provider.registerKey("key-1", KeyPurpose.CREDENTIAL_SIGNING, kp.getPrivate(), kp.getPublic());

        assertTrue(provider.hasPrivateKey("key-1", KeyPurpose.CREDENTIAL_SIGNING));
        assertTrue(provider.getPrivateKey("key-1", KeyPurpose.CREDENTIAL_SIGNING).isPresent());
    }

    @Test
    @DisplayName("Mismatched keypair fails closed on registration with IllegalStateException")
    void mismatchedKeypairFailsClosed() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp1 = gen.generateKeyPair();
        KeyPair kp2 = gen.generateKeyPair();

        DevelopmentSigningKeyProvider provider = new DevelopmentSigningKeyProvider();

        // Attempting to register private key of kp1 with public key of kp2 must fail closed
        assertThrows(IllegalStateException.class, () ->
                provider.registerKey("mismatch-key", KeyPurpose.CREDENTIAL_SIGNING, kp1.getPrivate(), kp2.getPublic()));

        assertFalse(provider.hasPrivateKey("mismatch-key", KeyPurpose.CREDENTIAL_SIGNING));
    }

    @Test
    @DisplayName("Unregistered key returns empty and fails closed")
    void unregisteredKeyReturnsEmpty() {
        DevelopmentSigningKeyProvider provider = new DevelopmentSigningKeyProvider();
        assertFalse(provider.hasPrivateKey("unknown", KeyPurpose.DOCUMENT_SIGNING));
        assertTrue(provider.getPrivateKey("unknown", KeyPurpose.DOCUMENT_SIGNING).isEmpty());
    }
}

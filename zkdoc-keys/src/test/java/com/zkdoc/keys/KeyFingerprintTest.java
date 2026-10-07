package com.zkdoc.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyFingerprintTest {

    @Test
    @DisplayName("Deterministic SHA-256 fingerprint generated from public key")
    void deterministicFingerprintFromKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        KeyFingerprint fp1 = KeyFingerprint.fromPublicKey(kp.getPublic());
        KeyFingerprint fp2 = KeyFingerprint.fromDer(kp.getPublic().getEncoded());

        assertNotNull(fp1);
        assertEquals(fp1, fp2);
        assertTrue(fp1.value().startsWith("SHA256:"));
        assertEquals(64, fp1.hex().length());
    }

    @Test
    @DisplayName("Fingerprint validation rejects malformed hex or null")
    void rejectsInvalidFingerprint() {
        assertThrows(NullPointerException.class, () -> new KeyFingerprint(null));
        assertThrows(IllegalArgumentException.class, () -> new KeyFingerprint("invalid-short"));
        assertThrows(IllegalArgumentException.class, () -> new KeyFingerprint("SHA256:1234"));
    }
}

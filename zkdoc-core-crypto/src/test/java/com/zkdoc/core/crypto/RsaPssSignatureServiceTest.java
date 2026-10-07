package com.zkdoc.core.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsaPssSignatureServiceTest {

    private static KeyPair keyPair2048;
    private static KeyPair keyPair3072;
    private static KeyPair otherKeyPair;
    private final RsaPssSignatureService signatureService = new RsaPssSignatureService();

    @BeforeAll
    static void generateTestKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair2048 = gen.generateKeyPair();
        otherKeyPair = gen.generateKeyPair();

        gen.initialize(3072);
        keyPair3072 = gen.generateKeyPair();
    }

    private static byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Sign and verify valid signature with 2048-bit RSA key")
    void signAndVerify2048() throws Exception {
        byte[] digest = sha256("document-payload-content-1");
        byte[] signature = signatureService.signRsaPss(digest, keyPair2048.getPrivate());

        assertNotNull(signature);
        assertTrue(signature.length > 0);
        assertTrue(signatureService.verifyRsaPss(digest, signature, keyPair2048.getPublic()));
    }

    @Test
    @DisplayName("Sign and verify valid signature with 3072-bit RSA key")
    void signAndVerify3072() throws Exception {
        byte[] digest = sha256("credential-canonical-payload-2");
        byte[] signature = signatureService.signRsaPss(digest, keyPair3072.getPrivate());

        assertNotNull(signature);
        assertTrue(signature.length > 0);
        assertTrue(signatureService.verifyRsaPss(digest, signature, keyPair3072.getPublic()));
    }

    @Test
    @DisplayName("Verification fails when digest is tampered")
    void verificationFailsOnTamperedDigest() throws Exception {
        byte[] originalDigest = sha256("original-payload");
        byte[] tamperedDigest = sha256("tampered-payload");

        byte[] signature = signatureService.signRsaPss(originalDigest, keyPair3072.getPrivate());
        assertFalse(signatureService.verifyRsaPss(tamperedDigest, signature, keyPair3072.getPublic()));
    }

    @Test
    @DisplayName("Verification fails when signature bytes are corrupted")
    void verificationFailsOnCorruptedSignature() throws Exception {
        byte[] digest = sha256("payload");
        byte[] signature = signatureService.signRsaPss(digest, keyPair3072.getPrivate());

        byte[] corrupted = Arrays.copyOf(signature, signature.length);
        corrupted[10] ^= 0xFF;

        assertFalse(signatureService.verifyRsaPss(digest, corrupted, keyPair3072.getPublic()));
    }

    @Test
    @DisplayName("Verification fails when verified against wrong public key")
    void verificationFailsWithWrongPublicKey() throws Exception {
        byte[] digest = sha256("payload");
        byte[] signature = signatureService.signRsaPss(digest, keyPair2048.getPrivate());

        assertFalse(signatureService.verifyRsaPss(digest, signature, otherKeyPair.getPublic()));
    }

    @Test
    @DisplayName("Verification returns false on null or empty inputs without throwing")
    void verificationGracefullyHandlesNullOrEmpty() throws Exception {
        byte[] digest = sha256("payload");
        byte[] validSignature = signatureService.signRsaPss(digest, keyPair2048.getPrivate());

        assertFalse(signatureService.verifyRsaPss(null, validSignature, keyPair2048.getPublic()));
        assertFalse(signatureService.verifyRsaPss(digest, null, keyPair2048.getPublic()));
        assertFalse(signatureService.verifyRsaPss(digest, validSignature, null));
        assertFalse(signatureService.verifyRsaPss(new byte[16], validSignature, keyPair2048.getPublic()));
        assertFalse(signatureService.verifyRsaPss(digest, new byte[0], keyPair2048.getPublic()));
    }

    @Test
    @DisplayName("Signing rejects invalid digest length")
    void signingRejectsInvalidDigestLength() {
        assertThrows(IllegalArgumentException.class, () ->
                signatureService.signRsaPss(new byte[16], keyPair2048.getPrivate()));
        assertThrows(IllegalArgumentException.class, () ->
                signatureService.signRsaPss(new byte[64], keyPair2048.getPrivate()));
    }

    @Test
    @DisplayName("Signing rejects sub-2048-bit keys")
    void signingRejectsWeakKeySize() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024);
        KeyPair weakPair = gen.generateKeyPair();
        byte[] digest = sha256("payload");

        assertThrows(IllegalArgumentException.class, () ->
                signatureService.signRsaPss(digest, weakPair.getPrivate()));
    }
}

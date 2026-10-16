package com.zkdoc.keys;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FIND-004 Remediation: ProductionSigningKeyProvider Tests")
class ProductionSigningKeyProviderTest {

    private static KeyPair keyPair1;
    private static KeyPair keyPair2;

    @BeforeAll
    static void initKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair1 = kpg.generateKeyPair();
        keyPair2 = kpg.generateKeyPair();
    }

    @Test
    @DisplayName("Loading valid PKCS#8 DER private key succeeds with consistency probe verified")
    void loadValidDerKeySucceeds() {
        ProductionSigningKeyProvider provider = new ProductionSigningKeyProvider();
        String keyId = "issuer-prod-key-1";

        provider.loadDerKey(keyId, KeyPurpose.DOCUMENT_SIGNING, keyPair1.getPrivate().getEncoded(), keyPair1.getPublic());

        assertTrue(provider.hasPrivateKey(keyId, KeyPurpose.DOCUMENT_SIGNING));
        assertTrue(provider.getPrivateKey(keyId, KeyPurpose.DOCUMENT_SIGNING).isPresent());
        assertEquals(1, provider.getLoadedKeyCount());
    }

    @Test
    @DisplayName("Loading valid PKCS#8 PEM string succeeds")
    void loadValidPemKeySucceeds() {
        ProductionSigningKeyProvider provider = new ProductionSigningKeyProvider();
        String keyId = "issuer-prod-pem-key";

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair1.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        provider.loadPemKey(keyId, KeyPurpose.CREDENTIAL_SIGNING, pem, keyPair1.getPublic());

        assertTrue(provider.hasPrivateKey(keyId, KeyPurpose.CREDENTIAL_SIGNING));
        assertFalse(provider.hasPrivateKey(keyId, KeyPurpose.DOCUMENT_SIGNING)); // Purpose isolation
    }

    @Test
    @DisplayName("Mismatched public key fails consistency probe and fails closed")
    void mismatchedPublicKeyFailsProbe() {
        ProductionSigningKeyProvider provider = new ProductionSigningKeyProvider();
        String keyId = "mismatched-key";

        // keyPair1 private key paired with keyPair2 public key
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                provider.loadDerKey(keyId, KeyPurpose.DOCUMENT_SIGNING, keyPair1.getPrivate().getEncoded(), keyPair2.getPublic())
        );

        assertTrue(ex.getMessage().contains("Configured production private key does not correspond to expected public key"));
        assertFalse(provider.hasPrivateKey(keyId, KeyPurpose.DOCUMENT_SIGNING));
        assertEquals(0, provider.getLoadedKeyCount());
    }

    @Test
    @DisplayName("Corrupted DER bytes throw IllegalArgumentException")
    void corruptDerBytesThrowsException() {
        ProductionSigningKeyProvider provider = new ProductionSigningKeyProvider();
        byte[] garbage = new byte[] {0x00, 0x01, 0x02, 0x03};

        assertThrows(IllegalArgumentException.class, () ->
                provider.loadDerKey("bad-der", KeyPurpose.DOCUMENT_SIGNING, garbage, keyPair1.getPublic())
        );
    }

    @Test
    @DisplayName("Loading key from secure file on disk succeeds")
    void loadKeyFromFileSucceeds(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("private-key.pem");
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair1.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        Files.writeString(keyFile, pem);

        ProductionSigningKeyProvider provider = new ProductionSigningKeyProvider();
        provider.loadKeyFromFile("file-key-1", KeyPurpose.DOCUMENT_SIGNING, keyFile, keyPair1.getPublic());

        assertTrue(provider.hasPrivateKey("file-key-1", KeyPurpose.DOCUMENT_SIGNING));
    }
}

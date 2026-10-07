package com.zkdoc.api.keys;

import com.zkdoc.api.Application;
import com.zkdoc.core.crypto.RsaPssSignatureService;
import com.zkdoc.keys.KeyFingerprint;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mandated Section 51 Restart Test:
 * Verifies that key identity, public keys, and cryptographic evidence
 * survive application restart without silent modification, replacement,
 * or regeneration.
 */
class KeyRestartPersistenceIntegrationTest {

    private static byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Mandated Section 51: Key identity and public key persist across application restart")
    void keyIdentityPersistsAcrossApplicationRestart() throws Exception {
        Path dbPath = Files.createTempDirectory("zkdoc-restart-it-").resolve("restart-it.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair originalKeyPair = gen.generateKeyPair();

        String keyId = "persistent-issuer-key-" + UUID.randomUUID();
        String originalFingerprint;
        byte[] originalDer;
        String originalPem;
        byte[] testSignature;
        byte[] testDigest = sha256("restart-test-payload-evidence");

        // ---- SESSION 1: Start application, register key, produce signature, then shutdown ----
        try (ConfigurableApplicationContext ctx1 = new SpringApplicationBuilder(Application.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .profiles("local")
                .properties("SPRING_DATASOURCE_URL=" + jdbcUrl, "spring.datasource.url=" + jdbcUrl)
                .run()) {

            KeyRegistry registry1 = ctx1.getBean(KeyRegistry.class);
            RegisteredKey registered = RegisteredKey.createActive(
                    keyId, KeyPurpose.CREDENTIAL_SIGNING, "issuer-persistent-domain", KeyOwnerType.ISSUER, originalKeyPair.getPublic());
            registry1.register(registered);

            RegisteredKey retrieved1 = registry1.findByKeyId(keyId).orElseThrow();
            originalFingerprint = retrieved1.fingerprint();
            originalDer = retrieved1.publicKeyDer();
            originalPem = retrieved1.publicKeyPem();

            RsaPssSignatureService sigService = new RsaPssSignatureService();
            testSignature = sigService.signRsaPss(testDigest, originalKeyPair.getPrivate());
            assertTrue(sigService.verifyRsaPss(testDigest, testSignature, retrieved1.toPublicKey()));
        }

        // ---- APPLICATION COMPLETELY STOPPED HERE ----

        // ---- SESSION 2: Start fresh application instance against the SAME database ----
        try (ConfigurableApplicationContext ctx2 = new SpringApplicationBuilder(Application.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .profiles("local")
                .properties("SPRING_DATASOURCE_URL=" + jdbcUrl, "spring.datasource.url=" + jdbcUrl)
                .run()) {

            KeyRegistry registry2 = ctx2.getBean(KeyRegistry.class);

            // Resolve Key A from the new application instance
            RegisteredKey retrieved2 = registry2.findByKeyId(keyId).orElseThrow();

            assertNotNull(retrieved2, "Key A must be resolved after restart");
            assertEquals(keyId, retrieved2.keyId(), "Key ID must be identical across restart");
            assertEquals(KeyStatus.ACTIVE, retrieved2.status(), "Key status must be preserved");
            assertEquals(originalFingerprint, retrieved2.fingerprint(), "Fingerprint must not change across restart");
            assertEquals(originalPem, retrieved2.publicKeyPem(), "Public key PEM must be bit-exact across restart");
            assertArrayEquals(originalDer, retrieved2.publicKeyDer(), "Public key DER must be bit-exact across restart");

            // Verify that historical cryptographic signatures produced in Session 1 verify in Session 2
            RsaPssSignatureService sigService = new RsaPssSignatureService();
            assertTrue(sigService.verifyRsaPss(testDigest, testSignature, retrieved2.toPublicKey()),
                    "Signatures produced prior to restart must verify successfully against restarted key identity");
        }
    }
}

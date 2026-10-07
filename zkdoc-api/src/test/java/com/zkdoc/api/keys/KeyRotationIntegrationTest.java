package com.zkdoc.api.keys;

import com.zkdoc.api.Application;
import com.zkdoc.keys.DevelopmentSigningKeyProvider;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mandated Section 50 Key Rotation and Historical Verification Integration Test:
 *
 * Key A ACTIVE
 *   ↓
 * Credential signed with A
 *   ↓
 * Key B created, B ACTIVE
 *   ↓
 * A RETIRED
 *   ↓
 * Credential signed with A remains verifiable
 *   ↓
 * New credential must use B (signing with retired A fails closed)
 *   ↓
 * A REVOKED
 *   ↓
 * Verification with A fails closed
 */
@SpringBootTest(classes = Application.class)
class KeyRotationIntegrationTest {

    private static final Path DB_PATH = createTempDb();

    @DynamicPropertySource
    static void configureSqlite(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    private static Path createTempDb() {
        try {
            return Files.createTempDirectory("zkdoc-rotation-it-").resolve("rotation-it.db");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create temp db", e);
        }
    }

    private static KeyPair keyPairA;
    private static KeyPair keyPairB;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPairA = gen.generateKeyPair();
        keyPairB = gen.generateKeyPair();
    }

    @Autowired private KeyRegistryService keyRegistryService;
    @Autowired private KeyRegistry keyRegistry;
    @Autowired private DevelopmentSigningKeyProvider signingKeyProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM key_lifecycle_audit_events");
        jdbcTemplate.update("DELETE FROM key_registry");
        signingKeyProvider.clear();
    }

    private static byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Mandated Section 50: Full Historical Rotation and Revocation Lifecycle Workflow")
    void executeSection50KeyRotationLifecycle() throws Exception {
        String keyAId = "issuer-key-a-" + UUID.randomUUID();
        String keyBId = "issuer-key-b-" + UUID.randomUUID();
        String issuerOwnerId = "dept-of-motor-vehicles";

        // Step 1: Key A is registered and ACTIVE
        RegisteredKey keyA = RegisteredKey.createActive(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, KeyOwnerType.ISSUER, keyPairA.getPublic());
        keyRegistryService.register(keyA);
        signingKeyProvider.registerKey(keyAId, KeyPurpose.CREDENTIAL_SIGNING, keyPairA.getPrivate(), keyPairA.getPublic());

        // Step 2: Credential 1 is signed with Key A
        byte[] credentialDigest1 = sha256("canonical-credential-payload-alpha");
        byte[] signature1 = keyRegistryService.sign(keyAId, KeyPurpose.CREDENTIAL_SIGNING, credentialDigest1);
        assertNotNull(signature1);
        Instant credential1IssuedAt = Instant.now();

        // Verify Credential 1 with Key A while Key A is ACTIVE
        assertTrue(keyRegistryService.verify(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, credentialDigest1, signature1));

        // Step 3: Replacement Key B is registered and ACTIVE
        RegisteredKey keyB = RegisteredKey.createActive(
                keyBId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, KeyOwnerType.ISSUER, keyPairB.getPublic());
        keyRegistryService.register(keyB);
        signingKeyProvider.registerKey(keyBId, KeyPurpose.CREDENTIAL_SIGNING, keyPairB.getPrivate(), keyPairB.getPublic());

        // Step 4: Key A is rotated to Key B (Key A -> RETIRED, replacement_key_id -> Key B)
        keyRegistryService.rotateKey(keyAId, keyBId);

        RegisteredKey rotatedA = keyRegistry.findByKeyId(keyAId).orElseThrow();
        assertEquals(KeyStatus.RETIRED, rotatedA.status());
        assertEquals(keyBId, rotatedA.replacementKeyId());

        // Step 5: Credential 1 signed with Key A REMAINS HISTORICALLY VERIFIABLE
        assertTrue(keyRegistryService.verifyHistorical(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, credential1IssuedAt, credentialDigest1, signature1));

        // Step 6: Attempting to produce a NEW signature with retired Key A must FAIL CLOSED
        assertThrows(IllegalStateException.class, () ->
                keyRegistryService.sign(keyAId, KeyPurpose.CREDENTIAL_SIGNING, sha256("new-credential-payload")));

        // Step 7: New Credential 2 is signed using new Key B
        byte[] credentialDigest2 = sha256("canonical-credential-payload-beta");
        byte[] signature2 = keyRegistryService.sign(keyBId, KeyPurpose.CREDENTIAL_SIGNING, credentialDigest2);
        assertNotNull(signature2);
        assertTrue(keyRegistryService.verify(
                keyBId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, credentialDigest2, signature2));

        // Step 8: Emergency action — Key A is confirmed COMPROMISED and is REVOKED
        keyRegistryService.revokeKey(keyAId, "Private key compromised via unauthorized extraction");

        RegisteredKey revokedA = keyRegistry.findByKeyId(keyAId).orElseThrow();
        assertEquals(KeyStatus.REVOKED, revokedA.status());

        // Step 9: Historical verification with revoked Key A now FAILS CLOSED
        assertFalse(keyRegistryService.verifyHistorical(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, credential1IssuedAt, credentialDigest1, signature1));

        // And new Key B continues to verify normally
        assertTrue(keyRegistryService.verify(
                keyBId, KeyPurpose.CREDENTIAL_SIGNING, issuerOwnerId, credentialDigest2, signature2));
    }
}

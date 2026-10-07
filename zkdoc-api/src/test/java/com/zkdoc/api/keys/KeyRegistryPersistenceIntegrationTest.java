package com.zkdoc.api.keys;

import com.zkdoc.api.Application;
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
import java.util.List;
import java.util.Optional;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class)
class KeyRegistryPersistenceIntegrationTest {

    private static final Path DB_PATH = createTempDb();

    @DynamicPropertySource
    static void configureSqlite(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    private static Path createTempDb() {
        try {
            return Files.createTempDirectory("zkdoc-keys-it-").resolve("keys-it.db");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create temp db", e);
        }
    }

    private static KeyPair testKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        testKeyPair = gen.generateKeyPair();
    }

    @Autowired private KeyRegistry keyRegistry;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM key_lifecycle_audit_events");
        jdbcTemplate.update("DELETE FROM key_registry");
    }

    @Test
    @DisplayName("Persist, retrieve, and map complete RegisteredKey trust record")
    void persistAndRetrieveRegisteredKey() {
        String keyId = "issuer-key-" + UUID.randomUUID();
        RegisteredKey key = RegisteredKey.createActive(
                keyId, KeyPurpose.CREDENTIAL_SIGNING, "issuer-dept-1", KeyOwnerType.ISSUER, testKeyPair.getPublic());

        keyRegistry.register(key);

        Optional<RegisteredKey> retrieved = keyRegistry.findByKeyId(keyId);
        assertTrue(retrieved.isPresent());
        RegisteredKey r = retrieved.get();

        assertEquals(keyId, r.keyId());
        assertEquals(KeyPurpose.CREDENTIAL_SIGNING, r.purpose());
        assertEquals(KeyStatus.ACTIVE, r.status());
        assertEquals("issuer-dept-1", r.ownerId());
        assertEquals(KeyOwnerType.ISSUER, r.ownerType());
        assertEquals(key.publicKeyPem(), r.publicKeyPem());
        assertArrayEquals(key.publicKeyDer(), r.publicKeyDer());
        assertEquals(key.fingerprint(), r.fingerprint());
        assertNotNull(r.createdAt());
        assertNotNull(r.activatedAt());
    }

    @Test
    @DisplayName("Find active key for signing filters by purpose and owner")
    void findActiveForSigning() {
        String keyId = "active-cred-key-" + UUID.randomUUID();
        RegisteredKey key = RegisteredKey.createActive(
                keyId, KeyPurpose.CREDENTIAL_SIGNING, "issuer-42", KeyOwnerType.ISSUER, testKeyPair.getPublic());
        keyRegistry.register(key);

        Optional<RegisteredKey> found = keyRegistry.findActiveForSigning(KeyPurpose.CREDENTIAL_SIGNING, "issuer-42");
        assertTrue(found.isPresent());
        assertEquals(keyId, found.get().keyId());

        // Different purpose returns empty
        assertTrue(keyRegistry.findActiveForSigning(KeyPurpose.DOCUMENT_SIGNING, "issuer-42").isEmpty());

        // Different owner returns empty
        assertTrue(keyRegistry.findActiveForSigning(KeyPurpose.CREDENTIAL_SIGNING, "issuer-other").isEmpty());
    }

    @Test
    @DisplayName("Key rotation updates old key to RETIRED, sets replacement pointer, and increments version")
    void keyRotationPersistence() {
        String oldKeyId = "old-key-" + UUID.randomUUID();
        String newKeyId = "new-key-" + UUID.randomUUID();

        RegisteredKey oldKey = RegisteredKey.createActive(
                oldKeyId, KeyPurpose.DOCUMENT_SIGNING, "platform", KeyOwnerType.PLATFORM, testKeyPair.getPublic());
        RegisteredKey newKey = RegisteredKey.createActive(
                newKeyId, KeyPurpose.DOCUMENT_SIGNING, "platform", KeyOwnerType.PLATFORM, testKeyPair.getPublic());

        keyRegistry.register(oldKey);
        keyRegistry.register(newKey);

        keyRegistry.rotateKey(oldKeyId, newKeyId);

        RegisteredKey rotatedOld = keyRegistry.findByKeyId(oldKeyId).orElseThrow();
        assertEquals(KeyStatus.RETIRED, rotatedOld.status());
        assertEquals(newKeyId, rotatedOld.replacementKeyId());
        assertNotNull(rotatedOld.retiredAt());
        assertTrue(rotatedOld.version() > 1);

        RegisteredKey activeNew = keyRegistry.findByKeyId(newKeyId).orElseThrow();
        assertEquals(KeyStatus.ACTIVE, activeNew.status());
    }

    @Test
    @DisplayName("List all keys registered by owner")
    void findAllByOwner() {
        String owner = "multi-key-owner-" + UUID.randomUUID();
        RegisteredKey k1 = RegisteredKey.createActive(
                "key-a-" + UUID.randomUUID(), KeyPurpose.CREDENTIAL_SIGNING, owner, KeyOwnerType.ISSUER, testKeyPair.getPublic());
        RegisteredKey k2 = RegisteredKey.createActive(
                "key-b-" + UUID.randomUUID(), KeyPurpose.DOCUMENT_SIGNING, owner, KeyOwnerType.ISSUER, testKeyPair.getPublic());

        keyRegistry.register(k1);
        keyRegistry.register(k2);

        List<RegisteredKey> keys = keyRegistry.findAllByOwner(owner);
        assertEquals(2, keys.size());
    }
}

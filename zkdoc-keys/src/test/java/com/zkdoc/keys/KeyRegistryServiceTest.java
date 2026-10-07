package com.zkdoc.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyRegistryServiceTest {

    private InMemoryKeyRegistry registry;
    private DevelopmentSigningKeyProvider keyProvider;
    private KeyRegistryService keyService;

    private KeyPair keyPairA;
    private KeyPair keyPairB;

    @BeforeEach
    void setUp() throws Exception {
        registry = new InMemoryKeyRegistry();
        keyProvider = new DevelopmentSigningKeyProvider();
        keyService = new KeyRegistryService(registry, keyProvider);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPairA = gen.generateKeyPair();
        keyPairB = gen.generateKeyPair();
    }

    private static byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Complete Key Lifecycle: Active signing -> Rotation -> Historical verification -> Revocation")
    void keyLifecycleAndRotationWorkflow() throws Exception {
        String keyAId = "issuer-key-2026-a";
        String keyBId = "issuer-key-2026-b";
        String owner = "issuer-corp-1";

        // 1. Register Key A
        RegisteredKey keyA = RegisteredKey.createActive(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, owner, KeyOwnerType.ISSUER, keyPairA.getPublic());
        keyService.register(keyA);
        keyProvider.registerKey(keyAId, KeyPurpose.CREDENTIAL_SIGNING, keyPairA.getPrivate(), keyPairA.getPublic());

        // 2. Sign credential with Key A
        byte[] digest1 = sha256("credential-payload-1");
        byte[] sig1 = keyService.sign(keyAId, KeyPurpose.CREDENTIAL_SIGNING, digest1);
        assertNotNull(sig1);
        assertTrue(keyService.verify(keyAId, KeyPurpose.CREDENTIAL_SIGNING, owner, digest1, sig1));

        // 3. Register replacement Key B
        RegisteredKey keyB = RegisteredKey.createActive(
                keyBId, KeyPurpose.CREDENTIAL_SIGNING, owner, KeyOwnerType.ISSUER, keyPairB.getPublic());
        keyService.register(keyB);
        keyProvider.registerKey(keyBId, KeyPurpose.CREDENTIAL_SIGNING, keyPairB.getPrivate(), keyPairB.getPublic());

        // 4. Rotate Key A to Key B
        keyService.rotateKey(keyAId, keyBId);

        // 5. Signing with retired Key A must now FAIL CLOSED
        assertThrows(IllegalStateException.class, () ->
                keyService.sign(keyAId, KeyPurpose.CREDENTIAL_SIGNING, sha256("new-payload")));

        // 6. Signing with new Key B must SUCCEED
        byte[] digest2 = sha256("credential-payload-2");
        byte[] sig2 = keyService.sign(keyBId, KeyPurpose.CREDENTIAL_SIGNING, digest2);
        assertTrue(keyService.verify(keyBId, KeyPurpose.CREDENTIAL_SIGNING, owner, digest2, sig2));

        // 7. Historical signature from Key A MUST REMAIN VERIFIABLE
        assertTrue(keyService.verifyHistorical(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, owner, Instant.now().minusSeconds(60), digest1, sig1));

        // 8. Revoke Key A due to subsequent compromise
        keyService.revokeKey(keyAId, "Private key leak suspected");

        // 9. Historical signature from Key A must now FAIL CLOSED (revocation invalidates trust)
        assertFalse(keyService.verifyHistorical(
                keyAId, KeyPurpose.CREDENTIAL_SIGNING, owner, Instant.now().minusSeconds(60), digest1, sig1));
    }

    @Test
    @DisplayName("Illegal state transitions (e.g. REVOKED to ACTIVE) are strictly rejected")
    void illegalTransitionsRejected() {
        RegisteredKey key = RegisteredKey.createActive(
                "key-lifecycle-1", KeyPurpose.DOCUMENT_SIGNING, "platform", KeyOwnerType.PLATFORM, keyPairA.getPublic());
        keyService.register(key);

        keyService.revokeKey("key-lifecycle-1", "Administrative revoke");

        // Attempting to move REVOKED -> ACTIVE must fail
        assertThrows(IllegalStateException.class, () ->
                keyService.updateStatus("key-lifecycle-1", KeyStatus.ACTIVE));
    }

    @Test
    @DisplayName("Purpose confusion: Key registered for JWT cannot sign or verify credentials")
    void purposeConfusionRejected() throws Exception {
        String keyId = "jwt-auth-key-1";
        RegisteredKey key = RegisteredKey.createActive(
                keyId, KeyPurpose.JWT_SIGNING, "auth-service", KeyOwnerType.SERVICE, keyPairA.getPublic());
        keyService.register(key);
        keyProvider.registerKey(keyId, KeyPurpose.JWT_SIGNING, keyPairA.getPrivate(), keyPairA.getPublic());

        byte[] digest = sha256("payload");

        // Attempting to sign credential with JWT key
        assertThrows(IllegalArgumentException.class, () ->
                keyService.sign(keyId, KeyPurpose.CREDENTIAL_SIGNING, digest));

        // Sign as JWT
        byte[] jwtSig = keyService.sign(keyId, KeyPurpose.JWT_SIGNING, digest);

        // Verifying as CREDENTIAL_SIGNING fails closed
        assertFalse(keyService.verify(keyId, KeyPurpose.CREDENTIAL_SIGNING, "auth-service", digest, jwtSig));
    }

    private static class InMemoryKeyRegistry implements KeyRegistry {
        private final List<RegisteredKey> keys = new ArrayList<>();

        @Override
        public Optional<RegisteredKey> findByKeyId(String keyId) {
            return keys.stream().filter(k -> k.keyId().equals(keyId)).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose) {
            return keys.stream().filter(k -> k.keyId().equals(keyId) && k.purpose() == purpose).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose, String expectedOwnerId) {
            return keys.stream().filter(k -> k.keyId().equals(keyId) && k.purpose() == purpose && k.ownerId().equals(expectedOwnerId)).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose) {
            return keys.stream().filter(k -> k.purpose() == purpose && k.status() == KeyStatus.ACTIVE).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose, String ownerId) {
            return keys.stream().filter(k -> k.purpose() == purpose && k.ownerId().equals(ownerId) && k.status() == KeyStatus.ACTIVE).findFirst();
        }

        @Override
        public List<RegisteredKey> findAllByOwner(String ownerId) {
            return keys.stream().filter(k -> k.ownerId().equals(ownerId)).toList();
        }

        @Override
        public void register(RegisteredKey key) {
            keys.add(key);
        }

        @Override
        public void updateStatus(String keyId, KeyStatus status, String revocationReason, String replacementKeyId) {
            for (int i = 0; i < keys.size(); i++) {
                RegisteredKey k = keys.get(i);
                if (k.keyId().equals(keyId)) {
                    keys.set(i, new RegisteredKey(
                            k.id(), k.keyId(), k.purpose(), status, k.algorithm(), k.algorithmParameters(),
                            k.ownerId(), k.ownerType(), k.publicKeyPem(), k.publicKeyDer(), k.publicKeyEncoding(),
                            k.fingerprint(), k.createdAt(), k.activatedAt(),
                            status == KeyStatus.RETIRED ? Instant.now() : k.retiredAt(),
                            status == KeyStatus.REVOKED ? Instant.now() : k.revokedAt(),
                            revocationReason, replacementKeyId, k.version() + 1, k.metadata()));
                    return;
                }
            }
        }

        @Override
        public void rotateKey(String oldKeyId, String newKeyId) {
            updateStatus(oldKeyId, KeyStatus.RETIRED, "Rotated to " + newKeyId, newKeyId);
        }
    }
}

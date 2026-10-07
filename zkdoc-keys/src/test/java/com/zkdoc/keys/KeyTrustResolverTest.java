package com.zkdoc.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyTrustResolverTest {

    private InMemoryKeyRegistry registry;
    private KeyTrustResolver resolver;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        registry = new InMemoryKeyRegistry();
        resolver = new KeyTrustResolver(registry);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @Test
    @DisplayName("Active key matching purpose and owner resolves successfully")
    void activeKeyResolves() {
        RegisteredKey key = RegisteredKey.createActive(
                "key-active-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-42", KeyOwnerType.ISSUER, keyPair.getPublic());
        registry.register(key);

        assertTrue(resolver.resolveVerificationKey("key-active-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-42").isPresent());
    }

    @Test
    @DisplayName("Resolution rejects when purpose mismatches (e.g. JWT key requested for credential signing)")
    void purposeMismatchRejects() {
        RegisteredKey key = RegisteredKey.createActive(
                "jwt-key-1", KeyPurpose.JWT_SIGNING, "platform", KeyOwnerType.PLATFORM, keyPair.getPublic());
        registry.register(key);

        assertFalse(resolver.resolveVerificationKey("jwt-key-1", KeyPurpose.CREDENTIAL_SIGNING, "platform").isPresent());
    }

    @Test
    @DisplayName("Resolution rejects when owner mismatches (cross-issuer substitution attack)")
    void ownerMismatchRejects() {
        RegisteredKey key = RegisteredKey.createActive(
                "cred-key-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-real", KeyOwnerType.ISSUER, keyPair.getPublic());
        registry.register(key);

        assertFalse(resolver.resolveVerificationKey("cred-key-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-attacker").isPresent());
    }

    @Test
    @DisplayName("Revoked key fails closed for all verification requests")
    void revokedKeyFailsClosed() {
        RegisteredKey key = RegisteredKey.createActive(
                "revoked-key-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-42", KeyOwnerType.ISSUER, keyPair.getPublic());
        registry.register(key);
        registry.updateStatus("revoked-key-1", KeyStatus.REVOKED, "Compromise detected", null);

        assertFalse(resolver.resolveVerificationKey("revoked-key-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-42").isPresent());
    }

    @Test
    @DisplayName("Retired key resolves for historical verification of past artifacts")
    void retiredKeyResolvesHistorical() {
        RegisteredKey key = RegisteredKey.createActive(
                "retired-key-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-42", KeyOwnerType.ISSUER, keyPair.getPublic());
        registry.register(key);
        registry.updateStatus("retired-key-1", KeyStatus.RETIRED, "Scheduled rotation", "new-key-2");

        assertTrue(resolver.resolveHistoricalVerificationKey(
                "retired-key-1", KeyPurpose.CREDENTIAL_SIGNING, "issuer-42", Instant.now().minusSeconds(3600)).isPresent());
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

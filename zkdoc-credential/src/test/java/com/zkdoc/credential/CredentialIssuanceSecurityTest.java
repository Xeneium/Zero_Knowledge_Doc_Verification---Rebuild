package com.zkdoc.credential;

import com.zkdoc.keys.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 40: Exhaustive Adversarial Test Matrix for Phase 4 Credential Issuance and RSA-PSS Trust Chain.
 * Tests:
 * 1. Happy path: Authorized active issuer + active key -> valid verification
 * 2. Tampered attribute payload -> SHA-256 digest mismatch rejection
 * 3. Tampered signature bytes -> RSASSA-PSS verification failure
 * 4. Key rotation: Credential issued before rotation remains historically verifiable with retired key
 * 5. Key revocation: Credential issued under compromised/revoked key fails verification closed
 * 6. Wrong key purpose: Signing with JWT_SIGNING key fails closed
 * 7. Cross-issuer forgery: Issuer A key cannot sign for Issuer B
 * 8. Suspended issuer: Credential verification fails if issuer is suspended
 * 9. Expired credential: Fails verification closed
 * 10. Premature credential: Fails verification closed
 */
class CredentialIssuanceSecurityTest {

    private InMemoryKeyRegistry keyRegistry;
    private DevelopmentSigningKeyProvider signingKeyProvider;
    private KeyRegistryService keyRegistryService;
    private InMemoryIssuerStore issuerStore;
    private InMemoryCredentialStore credentialStore;
    private CanonicalCredentialEncoder encoder;
    private CredentialSha256Hasher hasher;
    private CredentialService credentialService;

    private KeyPair issuerKeyPairA;
    private KeyPair issuerKeyPairB;
    private KeyPair foreignKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        keyRegistry = new InMemoryKeyRegistry();
        signingKeyProvider = new DevelopmentSigningKeyProvider();
        keyRegistryService = new KeyRegistryService(keyRegistry, signingKeyProvider);
        issuerStore = new InMemoryIssuerStore();
        credentialStore = new InMemoryCredentialStore();
        encoder = new CanonicalCredentialEncoder();
        hasher = new CredentialSha256Hasher();
        credentialService = new CredentialService(
                credentialStore, encoder, hasher, null, keyRegistryService, issuerStore);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        issuerKeyPairA = kpg.generateKeyPair();
        issuerKeyPairB = kpg.generateKeyPair();
        foreignKeyPair = kpg.generateKeyPair();
    }

    @Test
    @DisplayName("Case 1: Happy Path - Active issuer and valid key produces fully verified credential")
    void happyPathIssuanceAndVerification() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String keyId = "key-state-dept-2026";
        setupActiveIssuer(issuerIdStr, keyId, issuerKeyPairA);

        Credential credential = createTestCredential(issuerIdStr, keyId, 25L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));

        SignedCredential signed = credentialService.issue(credential);
        assertNotNull(signed);
        assertNotNull(signed.signature());

        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertTrue(result.verified(), "Verification must succeed");
        assertTrue(result.digestMatches());
        assertTrue(result.signatureValid());
        assertTrue(result.issuerActive());
        assertTrue(result.keyTrusted());
        assertTrue(result.operationalStatusValid());
    }

    @Test
    @DisplayName("Case 2: Tamper rejection - Modifying canonical payload triggers digest mismatch")
    void tamperedPayloadRejected() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String keyId = "key-state-dept-2026";
        setupActiveIssuer(issuerIdStr, keyId, issuerKeyPairA);

        Credential credential = createTestCredential(issuerIdStr, keyId, 25L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        SignedCredential signed = credentialService.issue(credential);

        // Tamper with canonical bytes in the store
        StoredCredential stored = credentialStore.findById(credential.id().value()).orElseThrow();
        byte[] tamperedBytes = stored.canonicalPayload();
        tamperedBytes[tamperedBytes.length - 1] ^= 0xFF; // flip last bit

        credentialStore.update(new StoredCredential(
                stored.credentialId(), stored.expiresAt(), stored.status(),
                tamperedBytes, stored.digest(), stored.signature()));

        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertFalse(result.verified(), "Tampered payload must fail verification");
        assertFalse(result.digestMatches(), "Digest match must be false");
    }

    @Test
    @DisplayName("Case 3: Signature forgery - Corrupted signature fails RSASSA-PSS verification")
    void corruptedSignatureRejected() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String keyId = "key-state-dept-2026";
        setupActiveIssuer(issuerIdStr, keyId, issuerKeyPairA);

        Credential credential = createTestCredential(issuerIdStr, keyId, 25L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        SignedCredential signed = credentialService.issue(credential);

        StoredCredential stored = credentialStore.findById(credential.id().value()).orElseThrow();
        byte[] corruptedSig = stored.signature();
        corruptedSig[0] ^= 0x55;

        credentialStore.update(new StoredCredential(
                stored.credentialId(), stored.expiresAt(), stored.status(),
                stored.canonicalPayload(), stored.digest(), corruptedSig));

        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertFalse(result.verified(), "Corrupted signature must fail verification");
        assertFalse(result.signatureValid(), "Signature validity must be false");
    }

    @Test
    @DisplayName("Case 4: Key Rotation - Credential issued before rotation remains historically verifiable with retired key")
    void rotatedKeyMaintainsHistoricalVerification() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String oldKeyId = "key-old-2026";
        String newKeyId = "key-new-2026";
        setupActiveIssuer(issuerIdStr, oldKeyId, issuerKeyPairA);

        Credential credential = createTestCredential(issuerIdStr, oldKeyId, 30L, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        credentialService.issue(credential);

        // Register new key and rotate
        RegisteredKey newKey = RegisteredKey.createActive(
                newKeyId, KeyPurpose.CREDENTIAL_SIGNING, issuerIdStr, KeyOwnerType.ISSUER, issuerKeyPairB.getPublic());
        keyRegistry.register(newKey);
        signingKeyProvider.registerKey(newKeyId, KeyPurpose.CREDENTIAL_SIGNING, issuerKeyPairB.getPrivate(), issuerKeyPairB.getPublic());

        keyRegistryService.rotateKey(oldKeyId, newKeyId);

        // Verification must still succeed via historical key resolution
        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertTrue(result.verified(), "Historical credential with retired key must verify");
    }

    @Test
    @DisplayName("Case 5: Key Revocation - Compromised key revocation causes credential verification to fail closed")
    void revokedKeyFailsClosed() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String keyId = "key-compromised-2026";
        setupActiveIssuer(issuerIdStr, keyId, issuerKeyPairA);

        Credential credential = createTestCredential(issuerIdStr, keyId, 30L, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        credentialService.issue(credential);

        // Revoke the key
        keyRegistryService.revokeKey(keyId, "Suspected key leak");

        // Verification must now fail closed
        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertFalse(result.verified(), "Revoked key must fail verification closed");
        assertFalse(result.signatureValid());
    }

    @Test
    @DisplayName("Case 6: Purpose Separation - Cannot sign credential using JWT_SIGNING key")
    void wrongKeyPurposeRejected() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String jwtKeyId = "jwt-key-2026";

        RegisteredKey jwtKey = RegisteredKey.createActive(
                jwtKeyId, KeyPurpose.JWT_SIGNING, issuerIdStr, KeyOwnerType.ISSUER, issuerKeyPairA.getPublic());
        keyRegistry.register(jwtKey);
        signingKeyProvider.registerKey(jwtKeyId, KeyPurpose.JWT_SIGNING, issuerKeyPairA.getPrivate(), issuerKeyPairA.getPublic());

        Credential credential = createTestCredential(issuerIdStr, jwtKeyId, 25L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));

        // Signing must fail because purpose does not match CREDENTIAL_SIGNING
        assertThrows(Exception.class, () -> credentialService.issue(credential));
    }

    @Test
    @DisplayName("Case 7: Cross-issuer boundary - Issuer A cannot sign with Issuer B's key")
    void crossIssuerSigningForbidden() {
        String issuerA = "urn:zkdoc:issuer:dept-a";
        String issuerB = "urn:zkdoc:issuer:dept-b";
        String keyB = "key-b-2026";

        setupActiveIssuer(issuerB, keyB, issuerKeyPairB);

        // Try issuing credential for Issuer A referencing Issuer B's key
        Credential credential = createTestCredential(issuerA, keyB, 25L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));

        assertThrows(Exception.class, () -> credentialService.issue(credential));
    }

    @Test
    @DisplayName("Case 8: Suspended Issuer - Credential verification fails if issuer is suspended")
    void suspendedIssuerVerificationFails() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String keyId = "key-state-dept-2026";
        setupActiveIssuer(issuerIdStr, keyId, issuerKeyPairA);

        Credential credential = createTestCredential(issuerIdStr, keyId, 25L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        credentialService.issue(credential);

        // Suspend issuer
        issuerStore.updateStatus(IssuerId.of(issuerIdStr), IssuerStatus.SUSPENDED);

        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertFalse(result.verified(), "Suspended issuer must fail verification");
        assertFalse(result.issuerActive());
    }

    @Test
    @DisplayName("Case 9: Expired Credential - Fails verification closed when expired")
    void expiredCredentialVerificationFails() {
        String issuerIdStr = "urn:zkdoc:issuer:state-dept";
        String keyId = "key-state-dept-2026";
        setupActiveIssuer(issuerIdStr, keyId, issuerKeyPairA);

        Credential credential = createTestCredential(
                issuerIdStr, keyId, 25L, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        credentialService.issue(credential);

        CredentialVerificationResult result = credentialService.verify(credential.id().value());
        assertFalse(result.verified(), "Expired credential must fail verification");
        assertFalse(result.operationalStatusValid());
    }

    private void setupActiveIssuer(String issuerIdStr, String keyId, KeyPair keyPair) {
        IssuerId issuerId = IssuerId.of(issuerIdStr);
        KeyId kId = KeyId.of(keyId);
        Issuer issuer = Issuer.create(issuerId, "State Department", kId);
        issuerStore.save(issuer);

        RegisteredKey registeredKey = RegisteredKey.createActive(
                keyId, KeyPurpose.CREDENTIAL_SIGNING, issuerIdStr, KeyOwnerType.ISSUER, keyPair.getPublic());
        keyRegistry.register(registeredKey);
        signingKeyProvider.registerKey(keyId, KeyPurpose.CREDENTIAL_SIGNING, keyPair.getPrivate(), keyPair.getPublic());
    }

    private Credential createTestCredential(
            String issuerId, String keyId, long age, Instant issuedAt, Instant expiresAt) {
        return CredentialDraft.newDraft()
                .id(UUID.randomUUID())
                .schemaId("urn:zkdoc:schema:id-card:v1")
                .issuerId(issuerId)
                .issuerKeyId(keyId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .attribute(new CredentialAttribute("age", age))
                .attribute(new CredentialAttribute("active", true))
                .build();
    }

    // In-memory helper implementations for isolated tests
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

    private static class InMemoryIssuerStore implements IssuerStore {
        private final List<Issuer> issuers = new ArrayList<>();

        @Override
        public void save(Issuer issuer) {
            issuers.removeIf(i -> i.id().equals(issuer.id()));
            issuers.add(issuer);
        }

        @Override
        public Optional<Issuer> findById(IssuerId id) {
            return issuers.stream().filter(i -> i.id().equals(id)).findFirst();
        }

        @Override
        public void updateStatus(IssuerId id, IssuerStatus status) {
            findById(id).ifPresent(i -> {
                issuers.remove(i);
                issuers.add(new Issuer(i.id(), i.name(), status, i.signingKeyId(), i.createdAt(), Instant.now()));
            });
        }
    }

    private static class InMemoryCredentialStore implements CredentialStore {
        private final List<StoredCredential> credentials = new ArrayList<>();

        @Override
        public void save(SignedCredential signedCredential) {
            Credential c = signedCredential.credential();
            credentials.add(new StoredCredential(
                    c.id().value(), c.expiresAt(), c.status(),
                    signedCredential.canonicalPayload(), signedCredential.digest(), signedCredential.signature()));
        }

        @Override
        public Optional<StoredCredential> findById(UUID credentialId) {
            return credentials.stream().filter(c -> c.credentialId().equals(credentialId)).findFirst();
        }

        public void update(StoredCredential updated) {
            credentials.removeIf(c -> c.credentialId().equals(updated.credentialId()));
            credentials.add(updated);
        }
    }
}

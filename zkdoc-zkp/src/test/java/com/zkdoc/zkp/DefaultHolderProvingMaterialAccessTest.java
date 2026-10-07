package com.zkdoc.zkp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialNotFoundException;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.HolderCredential;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.credential.HolderCredentialNotFoundException;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderCredentialStatus;
import com.zkdoc.credential.HolderCredentialStore;
import com.zkdoc.credential.HolderId;
import com.zkdoc.credential.SignedCredential;
import com.zkdoc.credential.StoredCredential;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultHolderProvingMaterialAccessTest {

    private InMemoryHolderCredentialStore holderStore;
    private InMemoryCredentialStore credentialStore;
    private InMemoryCommitmentStore commitmentStore;
    private InMemoryHolderProvingMaterialStore provingMaterialStore;
    private PedersenCommitmentService pedersenCommitmentService;
    private HolderCredentialService holderCredentialService;
    private DefaultHolderProvingMaterialAccess provingMaterialAccess;

    private HolderId holderAlice;
    private HolderId holderBob;
    private UUID credentialId;
    private CredentialId credIdObj;
    private HolderCredential activeHc;
    private byte[] testDigest;

    @BeforeEach
    void setUp() throws Exception {
        holderStore = new InMemoryHolderCredentialStore();
        credentialStore = new InMemoryCredentialStore();
        commitmentStore = new InMemoryCommitmentStore();
        provingMaterialStore = new InMemoryHolderProvingMaterialStore();

        CanonicalCredentialEncoder canonicalEncoder = new CanonicalCredentialEncoder();
        CredentialSha256Hasher hasher = new CredentialSha256Hasher();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        CredentialRsaPssSigner signer = new CredentialRsaPssSigner(kp);
        SecureRandomBlindingFactorSource blindingSource = new SecureRandomBlindingFactorSource();

        pedersenCommitmentService = new PedersenCommitmentService(
                commitmentStore, canonicalEncoder, hasher, signer, blindingSource);

        holderCredentialService = new HolderCredentialService(holderStore, credentialStore);

        provingMaterialAccess = new DefaultHolderProvingMaterialAccess(
                holderCredentialService,
                credentialStore,
                commitmentStore,
                provingMaterialStore,
                pedersenCommitmentService);

        holderAlice = HolderId.of("holder-alice");
        holderBob = HolderId.of("holder-bob");
        credentialId = UUID.randomUUID();
        credIdObj = CredentialId.of(credentialId);

        testDigest = MessageDigest.getInstance("SHA-256").digest("canonical-bytes".getBytes(StandardCharsets.UTF_8));

        // Save active stored credential
        StoredCredential stored = new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                "canonical-bytes".getBytes(StandardCharsets.UTF_8),
                testDigest,
                new byte[] {1, 2, 3});
        credentialStore.save(stored);

        // Provision holder credential for Alice
        activeHc = holderCredentialService.provision(holderAlice, credIdObj);

        // Commit attribute "age" = 25
        AttributeCommitmentResult commitResult = pedersenCommitmentService.commitWithOpening(
                credentialId, "age", 25, "test-issuer");

        // Save proving secret in holder store
        provingMaterialStore.save(holderAlice.value(), commitResult.openingSecret());
    }

    @Test
    @DisplayName("Success: Resolves valid HolderProofContext with cryptographic continuity verified")
    void resolveProofContextSuccess() {
        HolderProofContext context = provingMaterialAccess.resolveProofContext(
                holderAlice, activeHc.id(), "age");

        assertNotNull(context);
        assertEquals(activeHc.id(), context.holderCredentialId());
        assertEquals(credIdObj, context.credentialId());
        assertEquals("age", context.attributeName());
        assertEquals(25, context.attributeValue());
        assertNotNull(context.blindingFactor());
        assertNotNull(context.commitmentPoint());
        assertNotNull(context.attestationSignature());
        assertEquals("ZKDOC-BP-1.0", context.protocolVersion());

        // Ensure toString() does not leak sensitive values
        String str = context.toString();
        assertFalse(str.contains("attributeValue=25"));
        assertTrue(str.contains("attributeValue=[REDACTED]"));
        assertTrue(str.contains("blindingFactor=[REDACTED]"));
    }

    @Test
    @DisplayName("BOLA / IDOR Defense: Bob cannot resolve proof context for Alice's credential")
    void idorCrossHolderAccessRejected() {
        assertThrows(HolderCredentialNotFoundException.class, () ->
                provingMaterialAccess.resolveProofContext(holderBob, activeHc.id(), "age"));
    }

    @Test
    @DisplayName("Lifecycle Defense: Cannot resolve proof context for SUSPENDED credential")
    void suspendedCredentialRejected() {
        holderCredentialService.suspend(activeHc.id(), holderAlice);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                provingMaterialAccess.resolveProofContext(holderAlice, activeHc.id(), "age"));
        assertTrue(ex.getMessage().contains("not active: SUSPENDED"));
    }

    @Test
    @DisplayName("Lifecycle Defense: Cannot resolve proof context for REVOKED credential")
    void revokedCredentialRejected() {
        holderCredentialService.revoke(activeHc.id(), holderAlice);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                provingMaterialAccess.resolveProofContext(holderAlice, activeHc.id(), "age"));
        assertTrue(ex.getMessage().contains("not active: REVOKED"));
    }

    @Test
    @DisplayName("Issuer Lifecycle Defense: Cannot resolve proof context if authoritative credential is REVOKED")
    void inactiveAuthoritativeCredentialRejected() {
        credentialStore.save(new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.REVOKED,
                "canonical-bytes".getBytes(StandardCharsets.UTF_8),
                testDigest,
                new byte[] {1, 2, 3}));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                provingMaterialAccess.resolveProofContext(holderAlice, activeHc.id(), "age"));
        assertTrue(ex.getMessage().contains("Authoritative credential is not active: REVOKED"));
    }

    @Test
    @DisplayName("Integrity Defense: Digest mismatch fails closed")
    void digestTamperingFailsClosed() {
        byte[] tamperedDigest = new byte[32];
        tamperedDigest[0] = 0x77;
        credentialStore.save(new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                "canonical-bytes".getBytes(StandardCharsets.UTF_8),
                tamperedDigest,
                new byte[] {1, 2, 3}));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                provingMaterialAccess.resolveProofContext(holderAlice, activeHc.id(), "age"));
        assertTrue(ex.getMessage().contains("tampering or corruption detected"));
    }

    @Test
    @DisplayName("Cryptographic Continuity: Tampered opening value fails closed")
    void openingValueTamperingFailsClosed() {
        // Tamper with the secret opening value in the store (e.g. claim age is 26 instead of 25)
        AttributeOpeningSecret tampered = new AttributeOpeningSecret(
                credentialId, "age", 26, new byte[32], new byte[32]);
        provingMaterialStore.save(holderAlice.value(), tampered);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                provingMaterialAccess.resolveProofContext(holderAlice, activeHc.id(), "age"));
        assertTrue(ex.getMessage().contains("Cryptographic commitment opening verification failed"));
    }

    @Test
    @DisplayName("Zeroization: Destroying context erases blinding factor bytes")
    void contextDestroyZeroizesWitness() {
        HolderProofContext context = provingMaterialAccess.resolveProofContext(
                holderAlice, activeHc.id(), "age");

        assertFalse(context.isDestroyed());
        context.destroy();
        assertTrue(context.isDestroyed());
    }

    private static class InMemoryHolderCredentialStore implements HolderCredentialStore {
        private final List<HolderCredential> credentials = new ArrayList<>();

        @Override public void save(HolderCredential hc) { credentials.add(hc); }
        @Override public Optional<HolderCredential> findById(HolderCredentialId id) {
            return credentials.stream().filter(c -> c.id().equals(id)).findFirst();
        }
        @Override public Optional<HolderCredential> findByHolderIdAndCredentialId(HolderId h, CredentialId c) {
            return credentials.stream().filter(x -> x.holderId().equals(h) && x.credentialId().equals(c)).findFirst();
        }
        @Override public List<HolderCredential> findAllByHolderId(HolderId h) {
            return credentials.stream().filter(x -> x.holderId().equals(h)).toList();
        }
        @Override public void updateStatus(HolderCredentialId id, HolderCredentialStatus s) {
            for (int i = 0; i < credentials.size(); i++) {
                if (credentials.get(i).id().equals(id)) {
                    credentials.set(i, credentials.get(i).transitionTo(s));
                    return;
                }
            }
        }
        @Override public boolean existsByHolderIdAndCredentialId(HolderId h, CredentialId c) {
            return credentials.stream().anyMatch(x -> x.holderId().equals(h) && x.credentialId().equals(c));
        }
    }

    private static class InMemoryCredentialStore implements CredentialStore {
        private final List<StoredCredential> list = new ArrayList<>();

        public void save(StoredCredential c) {
            list.removeIf(x -> x.credentialId().equals(c.credentialId()));
            list.add(c);
        }
        @Override public void save(SignedCredential sc) {
            save(new StoredCredential(
                    sc.credential().id().value(),
                    sc.credential().expiresAt(),
                    sc.credential().status(),
                    sc.canonicalPayload(),
                    sc.digest(),
                    sc.signature()));
        }
        @Override public Optional<StoredCredential> findById(UUID id) {
            return list.stream().filter(c -> c.credentialId().equals(id)).findFirst();
        }
    }

    private static class InMemoryCommitmentStore implements CommitmentStore {
        private final List<AttributeCommitment> commitments = new ArrayList<>();

        @Override public boolean blindingFingerprintExists(byte[] fingerprint) {
            return commitments.stream().anyMatch(c -> MessageDigest.isEqual(c.blindingFingerprint(), fingerprint));
        }
        @Override public void save(AttributeCommitment c) { commitments.add(c); }
        @Override public Optional<AttributeCommitment> findLatestByCredentialIdAndAttributeName(UUID id, String name) {
            return commitments.stream().filter(c -> c.credentialId().equals(id) && c.attributeName().equals(name)).reduce((first, second) -> second);
        }
        @Override public Optional<AttributeCommitment> findByCredentialIdAndAttributeName(UUID id, String name) {
            return findLatestByCredentialIdAndAttributeName(id, name);
        }
        @Override public Optional<AttributeCommitment> findById(UUID id) {
            return commitments.stream().filter(c -> c.id().equals(id)).findFirst();
        }
    }

    private static class InMemoryHolderProvingMaterialStore implements HolderProvingMaterialStore {
        private final List<HolderSecretEntry> secrets = new ArrayList<>();

        record HolderSecretEntry(String holderId, AttributeOpeningSecret secret) {}

        @Override public void save(String holderId, AttributeOpeningSecret secret) {
            secrets.removeIf(s -> s.holderId().equals(holderId) &&
                    s.secret().credentialId().equals(secret.credentialId()) &&
                    s.secret().attributeName().equals(secret.attributeName()));
            secrets.add(new HolderSecretEntry(holderId, secret));
        }
        @Override public Optional<AttributeOpeningSecret> findByHolderAndCredentialAndAttribute(String h, UUID c, String attr) {
            return secrets.stream()
                    .filter(s -> s.holderId().equals(h) && s.secret().credentialId().equals(c) && s.secret().attributeName().equals(attr))
                    .map(HolderSecretEntry::secret)
                    .findFirst();
        }
        @Override public List<AttributeOpeningSecret> findAllByHolderAndCredential(String h, UUID c) {
            return secrets.stream()
                    .filter(s -> s.holderId().equals(h) && s.secret().credentialId().equals(c))
                    .map(HolderSecretEntry::secret)
                    .toList();
        }
        @Override public void deleteByHolderAndCredential(String h, UUID c) {
            secrets.removeIf(s -> s.holderId().equals(h) && s.secret().credentialId().equals(c));
        }
    }
}

package com.zkdoc.zkp.holder;

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
import com.zkdoc.zkp.AttributeCommitment;
import com.zkdoc.zkp.AttributeCommitmentResult;
import com.zkdoc.zkp.AttributeOpeningSecret;
import com.zkdoc.zkp.CommitmentStore;
import com.zkdoc.zkp.DefaultHolderProvingMaterialAccess;
import com.zkdoc.zkp.HolderProvingMaterialStore;
import com.zkdoc.zkp.PedersenCommitmentService;
import com.zkdoc.zkp.SecureRandomBlindingFactorSource;
import com.zkdoc.zkp.engine.BulletproofPredicateEngine;
import com.zkdoc.zkp.engine.PredicateOperator;
import com.zkdoc.zkp.engine.WeavechainBulletproofPredicateEngine;
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

class DefaultPredicateProofGenerationServiceTest {

    private InMemoryHolderCredentialStore holderStore;
    private InMemoryCredentialStore credentialStore;
    private InMemoryCommitmentStore commitmentStore;
    private InMemoryHolderProvingMaterialStore provingMaterialStore;
    private PedersenCommitmentService pedersenCommitmentService;
    private HolderCredentialService holderCredentialService;
    private DefaultHolderProvingMaterialAccess provingMaterialAccess;
    private BulletproofPredicateEngine predicateEngine;
    private DefaultPredicateProofGenerationService proofService;

    private HolderId holderAlice;
    private HolderId holderBob;
    private UUID credentialId;
    private CredentialId credIdObj;
    private HolderCredential aliceCredential;
    private byte[] testDigest;
    private final byte[] testNonce = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};

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

        predicateEngine = new WeavechainBulletproofPredicateEngine();

        proofService = new DefaultPredicateProofGenerationService(provingMaterialAccess, predicateEngine);

        holderAlice = HolderId.of("holder-alice");
        holderBob = HolderId.of("holder-bob");
        credentialId = UUID.randomUUID();
        credIdObj = CredentialId.of(credentialId);

        testDigest = MessageDigest.getInstance("SHA-256").digest("canonical-bytes".getBytes(StandardCharsets.UTF_8));

        // Active authoritative credential
        StoredCredential stored = new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                "canonical-bytes".getBytes(StandardCharsets.UTF_8),
                testDigest,
                new byte[] {1, 2, 3});
        credentialStore.save(stored);

        // Provision holder credential for Alice
        aliceCredential = holderCredentialService.provision(holderAlice, credIdObj);

        // Commit attribute "age" = 25
        AttributeCommitmentResult commitResult = pedersenCommitmentService.commitWithOpening(
                credentialId, "age", 25, "issuer-key-1");

        // Save proving secret in holder store
        provingMaterialStore.save(holderAlice.value(), commitResult.openingSecret());
    }

    @Test
    @DisplayName("Honest holder above threshold generates cryptographically valid proof")
    void testHonestHolderAboveThresholdGeneratesValidProof() {
        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce
        );

        HolderPredicateProof proof = proofService.generateProof(holderAlice, req);

        assertNotNull(proof);
        assertNotNull(proof.statement());
        assertNotNull(proof.proofArtifact());
        assertEquals("zkdoc-range-v1", proof.proofArtifact().proofVersion());
        assertEquals(64, proof.proofArtifact().bitSize());
        assertTrue(proof.proofArtifact().proofBytes().length > 0);

        // Verify independent cryptographic verification succeeds
        boolean verified = predicateEngine.verifyGreaterThanOrEqual(
                proof.statement().commitmentPoint(),
                18,
                testNonce,
                proof.proofArtifact()
        );
        assertTrue(verified, "Generated proof must verify against cryptographic engine");

        // Ensure private witness is redacted and not in statement
        String stmtString = proof.statement().toString();
        assertFalse(stmtString.contains("attributeValue"));
        String witnessString = new PrivateProofWitness(25, new byte[32]).toString();
        assertTrue(witnessString.contains("[REDACTED]"));
        assertFalse(witnessString.contains("25"));
    }

    @Test
    @DisplayName("Honest holder exact threshold generates cryptographically valid proof")
    void testHonestHolderExactThresholdGeneratesValidProof() {
        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                25,
                testNonce
        );

        HolderPredicateProof proof = proofService.generateProof(holderAlice, req);
        assertNotNull(proof);

        boolean verified = predicateEngine.verifyGreaterThanOrEqual(
                proof.statement().commitmentPoint(),
                25,
                testNonce,
                proof.proofArtifact()
        );
        assertTrue(verified, "Exact threshold proof must verify");
    }

    @Test
    @DisplayName("Unsatisfied predicate (threshold > attribute value) fails closed")
    void testUnsatisfiedPredicateFailsClosed() {
        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                30, // 25 < 30
                testNonce
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                proofService.generateProof(holderAlice, req));
        assertTrue(ex.getMessage().contains("not satisfied"));
    }

    @Test
    @DisplayName("IDOR Defense: Bob cannot generate proof for Alice's credential")
    void testCrossHolderIdorRejected() {
        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce
        );

        assertThrows(HolderCredentialNotFoundException.class, () ->
                proofService.generateProof(holderBob, req));
    }

    @Test
    @DisplayName("Lifecycle Defense: Cannot prove on SUSPENDED credential")
    void testSuspendedCredentialRejected() {
        holderCredentialService.suspend(aliceCredential.id(), holderAlice);

        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                proofService.generateProof(holderAlice, req));
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    @DisplayName("Lifecycle Defense: Cannot prove on REVOKED credential")
    void testRevokedCredentialRejected() {
        holderCredentialService.revoke(aliceCredential.id(), holderAlice);

        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                proofService.generateProof(holderAlice, req));
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    @DisplayName("Integrity Defense: Cannot prove if authoritative credential is revoked")
    void testAuthoritativeCredentialRevokedRejected() {
        credentialStore.save(new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.REVOKED,
                "canonical-bytes".getBytes(StandardCharsets.UTF_8),
                testDigest,
                new byte[] {1, 2, 3}));

        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                proofService.generateProof(holderAlice, req));
        assertTrue(ex.getMessage().contains("Authoritative credential is not active"));
    }

    @Test
    @DisplayName("Cryptographic Continuity Defense: Tampered opening value fails closed")
    void testTamperedOpeningFailsClosed() {
        AttributeOpeningSecret tampered = new AttributeOpeningSecret(
                credentialId, "age", 26, new byte[32], new byte[32]);
        provingMaterialStore.save(holderAlice.value(), tampered);

        HolderPredicateProofRequest req = new HolderPredicateProofRequest(
                aliceCredential.id(),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                proofService.generateProof(holderAlice, req));
        assertTrue(ex.getMessage().contains("Cryptographic commitment opening verification failed"));
    }

    // In-memory test doubles
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

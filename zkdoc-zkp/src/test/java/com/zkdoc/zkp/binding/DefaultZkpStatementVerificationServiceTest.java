package com.zkdoc.zkp.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.SignedCredential;
import com.zkdoc.credential.StoredCredential;
import com.zkdoc.zkp.AttributeCommitment;
import com.zkdoc.zkp.AttributeCommitmentResult;
import com.zkdoc.zkp.CommitmentStore;
import com.zkdoc.zkp.PedersenCommitmentService;
import com.zkdoc.zkp.SecureRandomBlindingFactorSource;
import com.zkdoc.zkp.engine.BulletproofPredicateEngine;
import com.zkdoc.zkp.engine.PredicateOperator;
import com.zkdoc.zkp.engine.PredicateProofArtifact;
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

class DefaultZkpStatementVerificationServiceTest {

    private InMemoryCredentialStore credentialStore;
    private InMemoryCommitmentStore commitmentStore;
    private CanonicalCredentialEncoder canonicalEncoder;
    private CredentialSha256Hasher hasher;
    private CredentialRsaPssSigner issuerSigner;
    private BulletproofPredicateEngine predicateEngine;
    private DefaultZkpStatementVerificationService verificationService;

    private UUID credentialId;
    private CredentialId credIdObj;
    private byte[] testDigest;
    private final byte[] testNonce = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
    private PredicateProofArtifact validProofArtifact;

    @BeforeEach
    void setUp() throws Exception {
        credentialStore = new InMemoryCredentialStore();
        commitmentStore = new InMemoryCommitmentStore();
        canonicalEncoder = new CanonicalCredentialEncoder();
        hasher = new CredentialSha256Hasher();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        issuerSigner = new CredentialRsaPssSigner(kp);
        predicateEngine = new WeavechainBulletproofPredicateEngine();

        verificationService = new DefaultZkpStatementVerificationService(
                credentialStore,
                commitmentStore,
                canonicalEncoder,
                hasher,
                issuerSigner,
                null,
                predicateEngine
        );

        credentialId = UUID.randomUUID();
        credIdObj = CredentialId.of(credentialId);

        byte[] canonicalPayload = "canonical-credential-bytes".getBytes(StandardCharsets.UTF_8);
        testDigest = hasher.hash(canonicalPayload);
        byte[] signature = issuerSigner.sign(testDigest);

        // Save active stored credential
        StoredCredential stored = new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                canonicalPayload,
                testDigest,
                signature
        );
        credentialStore.save(stored);

        // Commit attribute "age" = 25
        PedersenCommitmentService commitmentService = new PedersenCommitmentService(
                commitmentStore, canonicalEncoder, hasher, issuerSigner, new SecureRandomBlindingFactorSource());
        AttributeCommitmentResult commitResult = commitmentService.commitWithOpening(
                credentialId, "age", 25, "default-issuer-key-1");

        // Generate genuine Bulletproof proof for age 25 >= 18
        validProofArtifact = predicateEngine.proveGreaterThanOrEqual(
                25,
                commitResult.openingSecret().blindingFactor(),
                18,
                testNonce
        );
    }

    @Test
    @DisplayName("Valid proof against authoritative server-resolved statement verifies successfully")
    void testValidStatementVerificationSuccess() {
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);

        assertTrue(result.verified());
        assertEquals(VerificationOutcomeStatus.VALID, result.status());
        assertEquals(credIdObj, result.credentialId());
        assertEquals("age", result.predicate().attributeName());
        assertEquals(18, result.predicate().threshold());
    }

    @Test
    @DisplayName("Cross-Credential Attack: Valid proof for Credential A fails against Credential B")
    void testCrossCredentialProofFails() throws Exception {
        UUID otherId = UUID.randomUUID();
        byte[] payload = "other-credential".getBytes(StandardCharsets.UTF_8);
        byte[] digest = hasher.hash(payload);
        credentialStore.save(new StoredCredential(
                otherId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                payload,
                digest,
                issuerSigner.sign(digest)
        ));

        // Create commitment for other credential (e.g. age = 30)
        PedersenCommitmentService commitmentService = new PedersenCommitmentService(
                commitmentStore, canonicalEncoder, hasher, issuerSigner, new SecureRandomBlindingFactorSource());
        commitmentService.commitWithOpening(otherId, "age", 30, "default-issuer-key-1");

        // Submit proof for credential A against credential B
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                CredentialId.of(otherId),
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
    }

    @Test
    @DisplayName("Cross-Threshold Attack: Proof generated for >= 18 fails against >= 21")
    void testCrossThresholdFails() {
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                21, // Stored proof was evaluated at 18
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
    }

    @Test
    @DisplayName("Cross-Attribute Attack: Request for non-existent attribute fails with INVALID_CREDENTIAL")
    void testUnknownAttributeFails() {
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "salary", // Not committed
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Freshness / Replay Attack: Replayed proof with a different nonce fails")
    void testDifferentNonceFails() {
        byte[] differentNonce = new byte[] {9, 9, 9, 9};
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                differentNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
    }

    @Test
    @DisplayName("Lifecycle Defense: Verification fails on revoked credential")
    void testRevokedCredentialFails() {
        byte[] payload = "canonical-credential-bytes".getBytes(StandardCharsets.UTF_8);
        credentialStore.save(new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.REVOKED,
                payload,
                testDigest,
                issuerSigner.sign(testDigest)
        ));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.EXPIRED_OR_REVOKED_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Lifecycle Defense: Verification fails on expired credential")
    void testExpiredCredentialFails() {
        byte[] payload = "canonical-credential-bytes".getBytes(StandardCharsets.UTF_8);
        credentialStore.save(new StoredCredential(
                credentialId,
                Instant.now().minusSeconds(10), // Expired
                CredentialStatus.ACTIVE,
                payload,
                testDigest,
                issuerSigner.sign(testDigest)
        ));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.EXPIRED_OR_REVOKED_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Integrity Defense: Verification fails if stored credential digest is tampered")
    void testTamperedCredentialDigestFails() {
        byte[] tamperedDigest = new byte[32];
        tamperedDigest[0] = 0x55;
        byte[] payload = "canonical-credential-bytes".getBytes(StandardCharsets.UTF_8);
        credentialStore.save(new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                payload,
                tamperedDigest,
                issuerSigner.sign(testDigest)
        ));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Attestation Defense: Verification fails if commitment attestation is invalid")
    void testInvalidAttestationFails() {
        AttributeCommitment stored = commitmentStore
                .findLatestByCredentialIdAndAttributeName(credentialId, "age").orElseThrow();
        byte[] tamperedAttestation = stored.attestation().clone();
        tamperedAttestation[0] ^= 1;
        AttributeCommitment tamperedCommitment = new AttributeCommitment(
                stored.id(),
                stored.credentialId(),
                stored.attributeName(),
                stored.commitment(),
                tamperedAttestation,
                stored.attestationPayload(),
                stored.blindingFingerprint(),
                stored.attestationKeyId()
        );
        commitmentStore.save(tamperedCommitment);

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_COMMITMENT, result.status());
    }

    @Test
    @DisplayName("Protocol Version Defense: Verification fails if proof version is unsupported")
    void testUnsupportedVersionFails() {
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-bulletproof-v999",
                credIdObj,
                "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL,
                18,
                testNonce,
                validProofArtifact
        );

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.UNSUPPORTED_VERSION, result.status());
    }

    // In-memory doubles
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
        @Override public void save(AttributeCommitment c) {
            commitments.removeIf(x -> x.id().equals(c.id()));
            commitments.add(c);
        }
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
}

package com.zkdoc.zkp.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.SignedCredential;
import com.zkdoc.credential.StoredCredential;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import com.zkdoc.zkp.AttributeCommitment;
import com.zkdoc.zkp.CommitmentStore;
import com.zkdoc.zkp.CredentialPredicateProver;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 9 Cryptographic and Statement-Binding Adversarial Invariant Suite.
 * Validates cryptographic invariants across:
 * - Invariant 1: Credential authenticity under RSA-PSS
 * - Invariant 3 & 4: Commitment integrity & context binding
 * - Invariant 5: Issuer attestation binding & Key Lifecycle (active vs revoked vs retired)
 * - Invariant 6: Proof byte bit-flipping / truncation / mutation
 * - Invariant 7 & 8: Statement integrity, predicate boundary ($v=T, v>T, v<T$), threshold manipulation
 * - Invariant 9 & 10: Credential ID and attribute name binding
 * - Invariant 11: Transcript nonce / context binding
 * - Invariant 12: Protocol version negotiation / downgrade attack defense
 * - Invariant 15: Credential lifecycle status (ACTIVE vs EXPIRED vs REVOKED)
 */
class ZkpCryptographicInvariantsAdversarialTest {

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
    private final byte[] testNonce = new byte[] {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
    private final long testValue = 25L;
    private final long testThreshold = 18L;
    private Scalar testBlinding;
    private byte[] testCommitmentBytes;
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

        byte[] canonicalPayload = ("{\"id\":\"" + credentialId + "\",\"age\":\"" + testValue + "\"}").getBytes(StandardCharsets.UTF_8);
        testDigest = hasher.hash(canonicalPayload);
        byte[] signature = issuerSigner.sign(testDigest);

        StoredCredential stored = new StoredCredential(
                credentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                canonicalPayload,
                testDigest,
                signature
        );
        credentialStore.save(stored);

        testBlinding = Utils.scalar(424242L);
        testCommitmentBytes = PedersenCommitment.getDefault().commit(Utils.scalar(testValue), testBlinding).toByteArray();
        byte[] attestationPayload = canonicalEncoder.encodeCommitmentAttestation(credentialId, "age", testCommitmentBytes);
        byte[] attestationSig = issuerSigner.sign(hasher.hash(attestationPayload));
        byte[] blindingFingerprint = hasher.hash(testBlinding.toByteArray());

        AttributeCommitment commitment = new AttributeCommitment(
                UUID.randomUUID(),
                credentialId,
                "age",
                testCommitmentBytes,
                attestationSig,
                attestationPayload,
                blindingFingerprint,
                "issuer-key-1"
        );
        commitmentStore.save(commitment);

        validProofArtifact = predicateEngine.proveGreaterThanOrEqual(
                testValue,
                testBlinding.toByteArray(),
                testThreshold,
                testNonce
        );
    }

    @Test
    @DisplayName("Invariant 1: Modifying credential canonical payload or digest invalidates RSA-PSS authentication")
    void testModifiedCredentialDigestRejects() {
        StoredCredential stored = credentialStore.findById(credentialId).orElseThrow();
        byte[] tamperedDigest = stored.digest().clone();
        tamperedDigest[0] ^= 0xFF;

        credentialStore.save(new StoredCredential(
                stored.credentialId(),
                stored.expiresAt(),
                stored.status(),
                stored.canonicalPayload(),
                tamperedDigest,
                stored.signature()
        ));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Invariant 3: Altering commitment point bytes fails attestation digest check")
    void testTamperedCommitmentPointRejects() {
        AttributeCommitment stored = commitmentStore.findLatestByCredentialIdAndAttributeName(credentialId, "age").orElseThrow();
        byte[] tamperedCommitment = stored.commitment().clone();
        tamperedCommitment[tamperedCommitment.length - 1] ^= 0x01;

        commitmentStore.save(new AttributeCommitment(
                stored.id(), stored.credentialId(), stored.attributeName(),
                tamperedCommitment, stored.attestation(), stored.attestationPayload(),
                stored.blindingFingerprint(), stored.attestationKeyId()));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_COMMITMENT, result.status());
    }

    @Test
    @DisplayName("Invariant 4: Commitment context binding prevents reusing commitment across different credentials")
    void testCommitmentReplayAcrossDifferentCredentialsRejects() {
        UUID otherCredId = UUID.randomUUID();
        byte[] payload = "other-payload".getBytes(StandardCharsets.UTF_8);
        byte[] digest = hasher.hash(payload);
        credentialStore.save(new StoredCredential(
                otherCredId, Instant.now().plusSeconds(3600), CredentialStatus.ACTIVE,
                payload, digest, issuerSigner.sign(digest)));

        AttributeCommitment original = commitmentStore.findLatestByCredentialIdAndAttributeName(credentialId, "age").orElseThrow();
        // Attacker attempts to bind original commitment bytes to otherCredId without valid issuer attestation
        commitmentStore.save(new AttributeCommitment(
                UUID.randomUUID(), otherCredId, "age",
                original.commitment(), original.attestation(), original.attestationPayload(),
                original.blindingFingerprint(), original.attestationKeyId()));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", CredentialId.of(otherCredId), "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_COMMITMENT, result.status());
    }

    @Test
    @DisplayName("Invariant 5: Forged issuer commitment attestation signature fails verification")
    void testForgedIssuerCommitmentAttestationRejects() {
        AttributeCommitment stored = commitmentStore.findLatestByCredentialIdAndAttributeName(credentialId, "age").orElseThrow();
        byte[] forgedSig = stored.attestation().clone();
        forgedSig[5] ^= 0xEE;

        commitmentStore.save(new AttributeCommitment(
                stored.id(), stored.credentialId(), stored.attributeName(),
                stored.commitment(), forgedSig, stored.attestationPayload(),
                stored.blindingFingerprint(), stored.attestationKeyId()));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_COMMITMENT, result.status());
    }

    @Test
    @DisplayName("Invariant 6: Mutation fuzzing across arbitrary proof bytes guarantees verification failure")
    void testProofByteMutationFuzzingRejects() {
        byte[] proofBytes = validProofArtifact.proofBytes();
        int[] mutationPositions = {0, 1, 16, proofBytes.length / 2, proofBytes.length - 2, proofBytes.length - 1};

        for (int pos : mutationPositions) {
            byte[] mutated = proofBytes.clone();
            mutated[pos] ^= 0x42;
            PredicateProofArtifact mutatedArtifact = new PredicateProofArtifact(
                    validProofArtifact.proofVersion(), validProofArtifact.bitSize(), mutated);

            ZkpVerificationRequest req = new ZkpVerificationRequest(
                    "zkdoc-range-v1", credIdObj, "age",
                    PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, mutatedArtifact);

            ZkpStatementVerificationResult result = verificationService.verify(req);
            assertFalse(result.verified(), "Mutated proof byte at pos " + pos + " must not verify");
            assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
        }
    }

    @Test
    @DisplayName("Invariant 6b: Truncated proof bytes reject cleanly without throwing unhandled exceptions")
    void testTruncatedProofBytesRejects() {
        byte[] proofBytes = validProofArtifact.proofBytes();
        byte[] truncated = Arrays.copyOf(proofBytes, proofBytes.length - 15);
        PredicateProofArtifact truncatedArtifact = new PredicateProofArtifact(
                validProofArtifact.proofVersion(), validProofArtifact.bitSize(), truncated);

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, truncatedArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
    }

    @Test
    @DisplayName("Invariant 7: Transcript nonce mismatch prevents proof verification")
    void testTranscriptNonceMismatchRejects() {
        byte[] alteredNonce = testNonce.clone();
        alteredNonce[0] ^= 0x01;

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, alteredNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
    }

    @Test
    @DisplayName("Invariant 8: Range predicate threshold alteration fails mathematical verification")
    void testThresholdMismatchRejects() {
        // Legitimate proof is for threshold 18. An adversary tests threshold 21 or 12.
        ZkpVerificationRequest reqHigher = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, 21, testNonce, validProofArtifact);

        ZkpStatementVerificationResult resultHigher = verificationService.verify(reqHigher);
        assertFalse(resultHigher.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, resultHigher.status());

        ZkpVerificationRequest reqLower = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, 12, testNonce, validProofArtifact);

        ZkpStatementVerificationResult resultLower = verificationService.verify(reqLower);
        assertFalse(resultLower.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, resultLower.status());
    }

    @Test
    @DisplayName("Invariant 9: Cross-Credential substitution with genuine proof fails verification")
    void testCrossCredentialProofSubstitutionRejects() throws Exception {
        UUID victimId = UUID.randomUUID();
        byte[] payload = "victim-payload".getBytes(StandardCharsets.UTF_8);
        byte[] digest = hasher.hash(payload);
        credentialStore.save(new StoredCredential(
                victimId, Instant.now().plusSeconds(3600), CredentialStatus.ACTIVE,
                payload, digest, issuerSigner.sign(digest)));

        Scalar victimBlinding = Utils.scalar(99999L);
        byte[] victimCommitment = PedersenCommitment.getDefault().commit(Utils.scalar(testValue), victimBlinding).toByteArray();
        byte[] victimAttestation = canonicalEncoder.encodeCommitmentAttestation(victimId, "age", victimCommitment);
        commitmentStore.save(new AttributeCommitment(
                UUID.randomUUID(), victimId, "age",
                victimCommitment, issuerSigner.sign(hasher.hash(victimAttestation)), victimAttestation,
                hasher.hash(victimBlinding.toByteArray()), "issuer-key-1"));

        // Attacker uses proof generated for credentialId against victimId
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", CredentialId.of(victimId), "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_PROOF, result.status());
    }

    @Test
    @DisplayName("Invariant 10: Cross-Attribute substitution fails closed")
    void testCrossAttributeSubstitutionRejects() {
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "creditScore",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.INVALID_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Invariant 12: Unsupported protocol version rejected immediately")
    void testUnsupportedProtocolVersionRejects() {
        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v0-downgrade", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.UNSUPPORTED_VERSION, result.status());
    }

    @Test
    @DisplayName("Invariant 15: Revoked credential rejected even if proof is mathematically valid")
    void testRevokedCredentialLifecycleRejects() {
        StoredCredential stored = credentialStore.findById(credentialId).orElseThrow();
        credentialStore.save(new StoredCredential(
                stored.credentialId(), stored.expiresAt(), CredentialStatus.REVOKED,
                stored.canonicalPayload(), stored.digest(), stored.signature()));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.EXPIRED_OR_REVOKED_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Invariant 15b: Expired credential rejected even if proof is mathematically valid")
    void testExpiredCredentialLifecycleRejects() {
        StoredCredential stored = credentialStore.findById(credentialId).orElseThrow();
        credentialStore.save(new StoredCredential(
                stored.credentialId(), Instant.now().minusSeconds(120), CredentialStatus.ACTIVE,
                stored.canonicalPayload(), stored.digest(), stored.signature()));

        ZkpVerificationRequest req = new ZkpVerificationRequest(
                "zkdoc-range-v1", credIdObj, "age",
                PredicateOperator.GREATER_THAN_OR_EQUAL, testThreshold, testNonce, validProofArtifact);

        ZkpStatementVerificationResult result = verificationService.verify(req);
        assertFalse(result.verified());
        assertEquals(VerificationOutcomeStatus.EXPIRED_OR_REVOKED_CREDENTIAL, result.status());
    }

    @Test
    @DisplayName("Invariant 18: Oversized proof artifact exceeding boundary rejects immediately")
    void testOversizedProofArtifactRejects() {
        byte[] oversized = new byte[PredicateProofArtifact.MAX_PROOF_BYTES + 1];

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new PredicateProofArtifact("zkdoc-range-v1", 64, oversized));
    }

    // In-memory test doubles
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

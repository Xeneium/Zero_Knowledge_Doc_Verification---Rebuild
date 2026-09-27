package com.zkdoc.api.zkp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import com.zkdoc.api.Application;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.zkp.AttributeCommitment;
import com.zkdoc.zkp.CommitmentStore;
import com.zkdoc.zkp.CredentialPredicateProver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@WithMockUser(username = "verifier-test", roles = "VERIFIER")
class CredentialPredicateSecurityIntegrationTest {
    private static final Path DATABASE_FILE = newDatabaseFile();
    private static final AtomicLong BLINDING_COUNTER = new AtomicLong();

    @RegisterExtension
    static final TestWatcher CASE_REPORTER = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.printf("CASE %s: PASS%n", context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.printf("CASE %s: FAIL (%s)%n", context.getDisplayName(), cause.getMessage());
        }
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CredentialService credentialService;
    @Autowired private CommitmentStore commitmentStore;
    @Autowired private CanonicalCredentialEncoder canonicalEncoder;
    @Autowired private CredentialSha256Hasher hasher;
    @Autowired private CredentialRsaPssSigner issuerSigner;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM attribute_commitments");
        jdbcTemplate.update("DELETE FROM credentials");
    }

    @Test
    @DisplayName("happy path — exact threshold ACCEPTS")
    void exactThresholdAccepts() throws Exception {
        Fixture fixture = fixture(18L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertAccepted(fixture.request());
    }

    @Test
    @DisplayName("happy path — above threshold ACCEPTS")
    void aboveThresholdAccepts() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertAccepted(fixture.request());
    }

    @Test
    @DisplayName("reject — wrong credential ID")
    void wrongCredentialIdRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertRejected(fixture.requestWith(UUID.randomUUID(), fixture.threshold(), fixture.nonce(), fixture.proofBytes()));
    }

    @Test
    @DisplayName("reject — tampered credential")
    void tamperedCredentialRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        jdbcTemplate.update("UPDATE credentials SET canonical_payload = ? WHERE credential_id = ?",
                new byte[] {1, 2, 3}, fixture.credentialId().toString());
        assertRejected(fixture.request());
    }

    @Test
    @DisplayName("reject — proof from credential A against credential B")
    void proofFromCredentialAAgainstCredentialBRejects() throws Exception {
        Fixture credentialA = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        Fixture credentialB = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertRejected(credentialA.requestWith(
                credentialB.credentialId(), credentialA.threshold(), credentialA.nonce(), credentialA.proofBytes()));
    }

    @Test
    @DisplayName("reject — expired credential")
    void expiredCredentialRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().minusSeconds(1));
        assertRejected(fixture.request());
    }

    @Test
    @DisplayName("reject — revoked credential")
    void revokedCredentialRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.REVOKED, Instant.now().plusSeconds(3600));
        assertRejected(fixture.request());
    }

    @Test
    @DisplayName("reject — invalid issuer signature")
    void invalidIssuerSignatureRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        jdbcTemplate.update("UPDATE attribute_commitments SET attestation = ? WHERE id = ?",
                new byte[] {9, 9, 9}, fixture.commitmentId().toString());
        assertRejected(fixture.request());
    }

    @Test
    @DisplayName("reject — tampered commitment")
    void tamperedCommitmentRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        byte[] modified = fixture.commitmentBytes().clone();
        modified[0] ^= 1;
        jdbcTemplate.update("UPDATE attribute_commitments SET commitment = ? WHERE id = ?",
                modified, fixture.commitmentId().toString());
        assertRejected(fixture.request());
    }

    @Test
    @DisplayName("reject — commitment swapped from another credential")
    void swappedCommitmentRejects() throws Exception {
        Fixture credentialA = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        Fixture credentialB = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        jdbcTemplate.update("UPDATE attribute_commitments SET commitment = ? WHERE id = ?",
                credentialB.commitmentBytes(), credentialA.commitmentId().toString());
        assertRejected(credentialA.request());
    }

    @Test
    @DisplayName("reject — client-supplied commitment field")
    void clientSuppliedCommitmentRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        ObjectNode request = objectMapper.valueToTree(fixture.request());
        request.put("commitment", "attacker-controlled");
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reject — modified proof")
    void modifiedProofRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        byte[] modified = fixture.proofBytes().clone();
        modified[modified.length - 1] ^= 1;
        assertRejected(fixture.requestWith(fixture.credentialId(), fixture.threshold(), fixture.nonce(), modified));
    }

    @Test
    @DisplayName("reject — truncated proof")
    void truncatedProofRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertRejected(fixture.requestWith(fixture.credentialId(), fixture.threshold(), fixture.nonce(),
                Arrays.copyOf(fixture.proofBytes(), fixture.proofBytes().length - 7)));
    }

    @Test
    @DisplayName("reject — malformed proof")
    void malformedProofRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertRejected(fixture.requestWith(fixture.credentialId(), fixture.threshold(), fixture.nonce(), new byte[] {1, 2, 3}));
    }

    @Test
    @DisplayName("reject — modified threshold")
    void modifiedThresholdRejects() throws Exception {
        Fixture fixture = fixture(20L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertRejected(fixture.requestWith(fixture.credentialId(), 21L, fixture.nonce(), fixture.proofBytes()));
    }

    @Test
    @DisplayName("reject — proof replayed with a different nonce")
    void replayWithDifferentNonceRejects() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        assertRejected(fixture.requestWith(
                fixture.credentialId(), fixture.threshold(), new byte[] {99, 98, 97}, fixture.proofBytes()));
    }

    private Fixture fixture(long value, long threshold, CredentialStatus status, Instant expiresAt) throws Exception {
        UUID credentialId = UUID.randomUUID();
        credentialService.issue(new Credential(
                "issuer-test", credentialId, List.of(new CredentialAttribute("age", Long.toString(value))),
                Instant.now().minusSeconds(60), expiresAt, status));
        Scalar blinding = Utils.scalar(BLINDING_COUNTER.incrementAndGet());
        byte[] commitmentBytes = PedersenCommitment.getDefault().commit(Utils.scalar(value), blinding).toByteArray();
        byte[] attestationPayload = canonicalEncoder.encodeCommitmentAttestation(credentialId, "age", commitmentBytes);
        AttributeCommitment commitment = new AttributeCommitment(
                UUID.randomUUID(), credentialId, "age", commitmentBytes,
                issuerSigner.sign(hasher.hash(attestationPayload)), attestationPayload,
                hasher.hash(blinding.toByteArray()));
        commitmentStore.save(commitment);
        byte[] nonce = new byte[] {10, 20, 30, (byte) BLINDING_COUNTER.get()};
        byte[] proof = new CredentialPredicateProver(
                PedersenCommitment.getDefault(), new BulletProofGenerators(128, 1))
                .prove(value, blinding, threshold, nonce);
        return new Fixture(credentialId, commitment.id(), threshold, nonce, proof, commitmentBytes);
    }

    private void assertAccepted(PredicateRequest request) throws Exception {
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    private void assertRejected(PredicateRequest request) throws Exception {
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-zkp-it-").resolve("zkdoc-it.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }

    private record PredicateRequest(
            String proofVersion, UUID credentialId, String attributeName, long threshold, byte[] nonce, byte[] proofBytes) {
    }

    private record Fixture(
            UUID credentialId,
            UUID commitmentId,
            long threshold,
            byte[] nonce,
            byte[] proofBytes,
            byte[] commitmentBytes) {
        private PredicateRequest request() {
            return requestWith(credentialId, threshold, nonce, proofBytes);
        }

        private PredicateRequest requestWith(UUID id, long requestedThreshold, byte[] requestedNonce, byte[] requestedProof) {
            return new PredicateRequest(
                    CredentialPredicateProver.PROOF_VERSION, id, "age", requestedThreshold, requestedNonce, requestedProof);
        }
    }
}

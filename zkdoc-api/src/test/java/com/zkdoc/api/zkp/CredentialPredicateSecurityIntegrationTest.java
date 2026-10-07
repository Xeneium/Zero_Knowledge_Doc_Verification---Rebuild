package com.zkdoc.api.zkp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    @Test
    @DisplayName("reject — oversized proof bytes (>64KB) returns bad request")
    void oversizedProofRejectsWithBadRequest() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        byte[] oversizedProof = new byte[65537];
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(fixture.requestWith(
                                fixture.credentialId(), fixture.threshold(), fixture.nonce(), oversizedProof))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reject — oversized nonce (>64 bytes) returns bad request")
    void oversizedNonceRejectsWithBadRequest() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        byte[] oversizedNonce = new byte[65];
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(fixture.requestWith(
                                fixture.credentialId(), fixture.threshold(), oversizedNonce, fixture.proofBytes()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reject — empty nonce returns bad request")
    void emptyNonceRejectsWithBadRequest() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        byte[] emptyNonce = new byte[0];
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(fixture.requestWith(
                                fixture.credentialId(), fixture.threshold(), emptyNonce, fixture.proofBytes()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reject — negative threshold returns bad request")
    void negativeThresholdRejectsWithBadRequest() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(fixture.requestWith(
                                fixture.credentialId(), -5L, fixture.nonce(), fixture.proofBytes()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("privacy safety: verification response never leaks attribute values, witness or blinders")
    void verificationResponseDoesNotLeakSecrets() throws Exception {
        Fixture fixture = fixture(25L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        String responseContent = mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(fixture.request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andReturn().getResponse().getContentAsString();

        // Ensure minimal disclosure
        assertFalse(responseContent.contains("25"), "Response must not contain raw attribute value");
        assertFalse(responseContent.contains("blinding"), "Response must not contain blinder information");
        assertFalse(responseContent.contains("witness"), "Response must not contain witness information");
        assertFalse(responseContent.contains("commitment"), "Response must not disclose commitment bytes");
    }

    @Test
    @DisplayName("audit event integrity: records verification event with actor and outcome without leaking secrets")
    void auditLoggingRecordsVerificationSafely() throws Exception {
        Fixture fixture = fixture(21L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(fixture.request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        List<java.util.Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT * FROM verification_audit_events WHERE credential_id = ?", fixture.credentialId().toString());

        assertFalse(events.isEmpty(), "Audit event must be recorded for verification");
        java.util.Map<String, Object> event = events.get(0);
        assertEquals("verifier-test", event.get("actor_subject"));
        assertEquals("CREDENTIAL_PREDICATE_VERIFY", event.get("event_type"));
        assertEquals(1, ((Number) event.get("result")).intValue());
    }

    @Test
    @DisplayName("concurrent verification requests maintain thread-safety and state isolation")
    void concurrentVerificationsSucceed() throws Exception {
        Fixture fixtureA = fixture(25L, 20L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));
        Fixture fixtureB = fixture(30L, 18L, CredentialStatus.ACTIVE, Instant.now().plusSeconds(3600));

        int threadCount = 6;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    org.springframework.security.core.context.SecurityContext context =
                            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                    "verifier-test", "password",
                                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_VERIFIER"))));
                    org.springframework.security.core.context.SecurityContextHolder.setContext(context);

                    startLatch.await();
                    PredicateRequest req = (index % 2 == 0) ? fixtureA.request() : fixtureB.request();
                    mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsBytes(req)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.verified").value(true));
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    System.err.println("Concurrent verification worker error: " + e.getMessage());
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        org.junit.jupiter.api.Assertions.assertEquals(threadCount, successCount.get());
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

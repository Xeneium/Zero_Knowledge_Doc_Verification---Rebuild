package com.zkdoc.api.zkp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import com.zkdoc.api.Application;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialLifecycleService;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
class ZkpLifecycleSecurityIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CredentialService credentialService;
    @Autowired private CredentialLifecycleService credentialLifecycleService;
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
        jdbcTemplate.update("DELETE FROM credential_lifecycle_events");
        jdbcTemplate.update("DELETE FROM attribute_commitments");
        jdbcTemplate.update("DELETE FROM credentials");
    }

    @Test
    @DisplayName("ZKP predicate verification strictly enforces credential lifecycle across active, suspended, resumed, and revoked states")
    void predicateVerificationEnforcesCredentialLifecycle() throws Exception {
        UUID credentialId = UUID.randomUUID();
        long value = 25L;
        long threshold = 18L;

        credentialService.issue(new Credential(
                "issuer-test", credentialId, List.of(new CredentialAttribute("age", Long.toString(value))),
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), CredentialStatus.ACTIVE));

        Scalar blinding = Utils.scalar(12345L);
        byte[] commitmentBytes = PedersenCommitment.getDefault().commit(Utils.scalar(value), blinding).toByteArray();
        byte[] attestationPayload = canonicalEncoder.encodeCommitmentAttestation(credentialId, "age", commitmentBytes);
        AttributeCommitment commitment = new AttributeCommitment(
                UUID.randomUUID(), credentialId, "age", commitmentBytes,
                issuerSigner.sign(hasher.hash(attestationPayload)), attestationPayload,
                hasher.hash(blinding.toByteArray()));
        commitmentStore.save(commitment);

        byte[] nonce = new byte[]{1, 2, 3, 4};
        byte[] proof = new CredentialPredicateProver(
                PedersenCommitment.getDefault(), new BulletProofGenerators(128, 1))
                .prove(value, blinding, threshold, nonce);

        PredicateRequest request = new PredicateRequest(
                CredentialPredicateProver.PROOF_VERSION, credentialId, "age", threshold, nonce, proof);

        // 1. ACTIVE: verified = true
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // 2. SUSPENDED: verified = false
        credentialLifecycleService.suspend(credentialId, "issuer-test", "ISSUER", "Routine check");
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));

        // 3. RESUMED to ACTIVE: verified = true
        credentialLifecycleService.resume(credentialId, "issuer-test", "ISSUER", "Check completed");
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // 4. REVOKED: verified = false
        credentialLifecycleService.revoke(credentialId, "issuer-test", "ISSUER", "Permanently revoked");
        mockMvc.perform(post("/api/v2/zkp/credential-predicates/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-zkp-lifecycle-it-").resolve("zkdoc-zkp-lifecycle.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }

    private record PredicateRequest(
            String proofVersion, UUID credentialId, String attributeName, long threshold, byte[] nonce, byte[] proofBytes) {
    }
}

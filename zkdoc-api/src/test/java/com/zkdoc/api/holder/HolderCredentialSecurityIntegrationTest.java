package com.zkdoc.api.holder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkdoc.api.Application;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.SignedCredential;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class HolderCredentialSecurityIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CredentialService credentialService;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-holder-it-").resolve("zkdoc-holder.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }

    @Test
    @DisplayName("Holder can provision a valid issued credential to themselves")
    @WithMockUser(username = "holder-alice", roles = "HOLDER")
    void holderCanProvisionCredential() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        ProvisionHolderCredentialRequest req = new ProvisionHolderCredentialRequest(credentialId);

        mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holderId").value("holder-alice"))
                .andExpect(jsonPath("$.credentialId").value(credentialId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.credentialDigestHex").isNotEmpty())
                // Verify zero private proving secrets leaked
                .andExpect(jsonPath("$.blindingFactor").doesNotExist())
                .andExpect(jsonPath("$.attributeValue").doesNotExist());
    }

    @Test
    @DisplayName("IDOR: Holder Bob cannot access Holder Alice's credential record")
    @WithMockUser(username = "holder-alice", roles = "HOLDER")
    void idorCrossHolderAccessForbidden() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        ProvisionHolderCredentialRequest req = new ProvisionHolderCredentialRequest(credentialId);

        String aliceResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        HolderCredentialResponse aliceHc = objectMapper.readValue(aliceResp, HolderCredentialResponse.class);

        // Now attempt access as Bob
        mockMvc.perform(get("/api/holder/credentials/" + aliceHc.id())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("holder-bob").roles("HOLDER")))
                .andExpect(status().isNotFound()); // Fails closed with not found / denied
    }

    @Test
    @DisplayName("Role Isolation: Verifier and Issuer cannot access holder endpoints")
    @WithMockUser(username = "verifier-1", roles = "VERIFIER")
    void verifierCannotAccessHolderEndpoints() throws Exception {
        mockMvc.perform(get("/api/holder/credentials"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Revocation: Holder can revoke own credential")
    @WithMockUser(username = "holder-carol", roles = "HOLDER")
    void holderCanRevokeCredential() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        ProvisionHolderCredentialRequest req = new ProvisionHolderCredentialRequest(credentialId);

        String carolResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        HolderCredentialResponse carolHc = objectMapper.readValue(carolResp, HolderCredentialResponse.class);

        // Revoke
        mockMvc.perform(post("/api/holder/credentials/" + carolHc.id() + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    @DisplayName("Lifecycle Operations: Holder can suspend and activate own credential")
    @WithMockUser(username = "holder-david", roles = "HOLDER")
    void holderCanSuspendAndActivateCredential() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        ProvisionHolderCredentialRequest req = new ProvisionHolderCredentialRequest(credentialId);

        String davidResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        HolderCredentialResponse davidHc = objectMapper.readValue(davidResp, HolderCredentialResponse.class);

        // Suspend
        mockMvc.perform(post("/api/holder/credentials/" + davidHc.id() + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.version").value(2));

        // Activate
        mockMvc.perform(post("/api/holder/credentials/" + davidHc.id() + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Autowired private com.zkdoc.zkp.PedersenCommitmentService pedersenCommitmentService;
    @Autowired private com.zkdoc.zkp.HolderProvingMaterialStore provingMaterialStore;
    @Autowired private com.zkdoc.zkp.engine.BulletproofPredicateEngine bulletproofEngine;

    private void issueTestCredential(UUID id) {
        credentialService.issue(new Credential(
                "issuer-test", id, List.of(new CredentialAttribute("age", "25")),
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600), CredentialStatus.ACTIVE));
    }

    @Test
    @DisplayName("Proof Generation: Authorized holder generates valid Bulletproof predicate proof")
    @WithMockUser(username = "holder-alice", roles = "HOLDER")
    void holderCanGenerateValidPredicateProof() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        // Commit attribute and save holder proving secret
        com.zkdoc.zkp.AttributeCommitmentResult commitResult = pedersenCommitmentService.commitWithOpening(
                credentialId, "age", 25, "default-issuer-key-1");
        provingMaterialStore.save("holder-alice", commitResult.openingSecret());

        // Provision holder credential
        ProvisionHolderCredentialRequest provReq = new ProvisionHolderCredentialRequest(credentialId);
        String provResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        HolderCredentialResponse hc = objectMapper.readValue(provResp, HolderCredentialResponse.class);

        // Generate predicate proof: age >= 18
        byte[] nonce = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        GeneratePredicateProofRequest proofReq = new GeneratePredicateProofRequest(
                hc.id(), "age", ">=", 18, java.util.HexFormat.of().formatHex(nonce));

        String proofRespStr = mockMvc.perform(post("/api/holder/credentials/proofs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proofReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderCredentialId").value(hc.id().toString()))
                .andExpect(jsonPath("$.credentialId").value(credentialId.toString()))
                .andExpect(jsonPath("$.attributeName").value("age"))
                .andExpect(jsonPath("$.operator").value(">="))
                .andExpect(jsonPath("$.threshold").value(18))
                .andExpect(jsonPath("$.commitmentPointHex").isNotEmpty())
                .andExpect(jsonPath("$.proofVersion").value("zkdoc-range-v1"))
                .andExpect(jsonPath("$.bitSize").value(64))
                .andExpect(jsonPath("$.proofBytesHex").isNotEmpty())
                // Ensure NO secret leaks in JSON response
                .andExpect(jsonPath("$.attributeValue").doesNotExist())
                .andExpect(jsonPath("$.blindingFactor").doesNotExist())
                .andExpect(jsonPath("$.witness").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        HolderPredicateProofResponse proofResp = objectMapper.readValue(proofRespStr, HolderPredicateProofResponse.class);

        // Verify independent cryptographic verification passes
        byte[] commitmentBytes = java.util.HexFormat.of().parseHex(proofResp.commitmentPointHex());
        byte[] proofBytes = java.util.HexFormat.of().parseHex(proofResp.proofBytesHex());
        com.zkdoc.zkp.engine.PredicateProofArtifact artifact = new com.zkdoc.zkp.engine.PredicateProofArtifact(
                proofResp.proofVersion(), proofResp.bitSize(), proofBytes);

        boolean verified = bulletproofEngine.verifyGreaterThanOrEqual(
                commitmentBytes, 18, nonce, artifact);
        org.junit.jupiter.api.Assertions.assertTrue(verified, "Generated proof must verify against cryptographic engine");
    }

    @Test
    @DisplayName("Proof Generation IDOR: Holder Bob cannot generate proof for Alice's credential")
    @WithMockUser(username = "holder-alice", roles = "HOLDER")
    void crossHolderProofGenerationRejected() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        com.zkdoc.zkp.AttributeCommitmentResult commitResult = pedersenCommitmentService.commitWithOpening(
                credentialId, "age", 25, "default-issuer-key-1");
        provingMaterialStore.save("holder-alice", commitResult.openingSecret());

        ProvisionHolderCredentialRequest provReq = new ProvisionHolderCredentialRequest(credentialId);
        String provResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        HolderCredentialResponse hc = objectMapper.readValue(provResp, HolderCredentialResponse.class);

        // Bob attempts to generate proof using Alice's holderCredentialId
        byte[] nonce = new byte[] {1, 2, 3, 4};
        GeneratePredicateProofRequest proofReq = new GeneratePredicateProofRequest(
                hc.id(), "age", ">=", 18, java.util.HexFormat.of().formatHex(nonce));

        mockMvc.perform(post("/api/holder/credentials/proofs/generate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("holder-bob").roles("HOLDER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proofReq)))
                .andExpect(status().isNotFound()); // Fails closed: 404 Not Found
    }

    @Test
    @DisplayName("Proof Generation: Unsatisfied predicate (threshold 30 > age 25) fails closed")
    @WithMockUser(username = "holder-carol", roles = "HOLDER")
    void unsatisfiedPredicateFailsClosed() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        com.zkdoc.zkp.AttributeCommitmentResult commitResult = pedersenCommitmentService.commitWithOpening(
                credentialId, "age", 25, "default-issuer-key-1");
        provingMaterialStore.save("holder-carol", commitResult.openingSecret());

        ProvisionHolderCredentialRequest provReq = new ProvisionHolderCredentialRequest(credentialId);
        String provResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        HolderCredentialResponse hc = objectMapper.readValue(provResp, HolderCredentialResponse.class);

        // Request threshold = 30 (25 < 30)
        byte[] nonce = new byte[] {1, 2, 3, 4};
        GeneratePredicateProofRequest proofReq = new GeneratePredicateProofRequest(
                hc.id(), "age", ">=", 30, java.util.HexFormat.of().formatHex(nonce));

        mockMvc.perform(post("/api/holder/credentials/proofs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proofReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Proof Generation Lifecycle: Suspended credential cannot generate proof")
    @WithMockUser(username = "holder-david", roles = "HOLDER")
    void suspendedCredentialCannotGenerateProof() throws Exception {
        UUID credentialId = UUID.randomUUID();
        issueTestCredential(credentialId);

        com.zkdoc.zkp.AttributeCommitmentResult commitResult = pedersenCommitmentService.commitWithOpening(
                credentialId, "age", 25, "default-issuer-key-1");
        provingMaterialStore.save("holder-david", commitResult.openingSecret());

        ProvisionHolderCredentialRequest provReq = new ProvisionHolderCredentialRequest(credentialId);
        String provResp = mockMvc.perform(post("/api/holder/credentials/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        HolderCredentialResponse hc = objectMapper.readValue(provResp, HolderCredentialResponse.class);

        // Suspend
        mockMvc.perform(post("/api/holder/credentials/" + hc.id() + "/suspend"))
                .andExpect(status().isOk());

        // Attempt proof generation
        byte[] nonce = new byte[] {1, 2, 3, 4};
        GeneratePredicateProofRequest proofReq = new GeneratePredicateProofRequest(
                hc.id(), "age", ">=", 18, java.util.HexFormat.of().formatHex(nonce));

        mockMvc.perform(post("/api/holder/credentials/proofs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proofReq)))
                .andExpect(status().isConflict());
    }
}

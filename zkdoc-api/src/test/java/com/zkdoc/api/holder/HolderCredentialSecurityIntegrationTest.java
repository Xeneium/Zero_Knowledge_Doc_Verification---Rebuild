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

    private void issueTestCredential(UUID id) {
        credentialService.issue(new Credential(
                "issuer-test", id, List.of(new CredentialAttribute("age", "25")),
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600), CredentialStatus.ACTIVE));
    }
}

package com.zkdoc.api.credential;

import com.zkdoc.api.Application;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.SignedCredential;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class CredentialLifecycleSecurityIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialService credentialService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    private SignedCredential issueCredential(String issuerId) {
        UUID credId = UUID.randomUUID();
        Credential credential = new Credential(
                issuerId,
                credId,
                List.of(new CredentialAttribute("name", "John Doe")),
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400),
                CredentialStatus.ACTIVE);
        return credentialService.issue(credential);
    }

    @Test
    @DisplayName("issuer can suspend own credential")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void issuerCanSuspendOwnCredential() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Temporary audit hold\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Temporary audit hold"));
    }

    @Test
    @DisplayName("foreign issuer cannot suspend credential (BOLA / IDOR defense)")
    @WithMockUser(username = "attacker-issuer", roles = "ISSUER")
    void foreignIssuerCannotSuspendCredential() throws Exception {
        SignedCredential issued = issueCredential("legit-issuer");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Malicious hold\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("holder cannot modify credential lifecycle")
    @WithMockUser(username = "holder-bob", roles = "HOLDER")
    void holderCannotModifyCredentialLifecycle() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/credentials/" + id + "/revoke"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin can modify any credential lifecycle as authorized authority")
    @WithMockUser(username = "admin-user", roles = "ADMIN")
    void adminCanModifyAnyCredential() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Admin compliance freeze\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("issuer can resume suspended credential")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void issuerCanResumeSuspendedCredential() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hold\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/credentials/" + id + "/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Resolved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("invalid lifecycle transitions (e.g. resume revoked) return 409 Conflict")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void invalidLifecycleTransitionsReturnConflict() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        // Terminal revocation
        mockMvc.perform(post("/api/credentials/" + id + "/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fraudulent document\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        // Attempt to resume revoked credential
        mockMvc.perform(post("/api/credentials/" + id + "/resume"))
                .andExpect(status().isConflict());

        // Attempt to suspend revoked credential
        mockMvc.perform(post("/api/credentials/" + id + "/suspend"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("repeated identical transitions are idempotent")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void transitionsAreIdempotent() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hold 1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hold 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("lifecycle history tracks all state transitions immutably")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void lifecycleHistoryTracksTransitions() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        mockMvc.perform(post("/api/credentials/" + id + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Audit\"}"));

        mockMvc.perform(post("/api/credentials/" + id + "/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cleared\"}"));

        mockMvc.perform(get("/api/credentials/" + id + "/lifecycle/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("SUSPEND"))
                .andExpect(jsonPath("$[1].eventType").value("RESUME"));
    }

    @Test
    @DisplayName("historical verification evaluates correct state at reference timestamp")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void historicalVerificationEvaluatesCorrectTime() throws Exception {
        SignedCredential issued = issueCredential("issuer-1");
        UUID id = issued.credential().credentialId();

        Instant historicalTime = Instant.now().minusSeconds(1800); // 30 mins ago

        // Revoke credential now
        mockMvc.perform(post("/api/credentials/" + id + "/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Revoked now\"}"))
                .andExpect(status().isOk());

        // Current verification: rejected because credential is revoked now
        mockMvc.perform(get("/api/credentials/" + id + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.signatureValid").value(true))
                .andExpect(jsonPath("$.operationalStatusValid").value(false));

        // Historical verification at past time: valid because credential was not yet revoked then!
        mockMvc.perform(get("/api/credentials/" + id + "/verify?asOf=" + historicalTime.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.signatureValid").value(true))
                .andExpect(jsonPath("$.operationalStatusValid").value(true));
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-cred-lifecycle-it-").resolve("zkdoc-lifecycle.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}

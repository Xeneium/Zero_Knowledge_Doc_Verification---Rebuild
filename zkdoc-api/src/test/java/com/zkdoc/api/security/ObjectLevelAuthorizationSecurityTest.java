package com.zkdoc.api.security;

import com.zkdoc.api.Application;
import com.zkdoc.api.qr.CapabilityId;
import com.zkdoc.api.qr.VerificationCapabilityService;
import com.zkdoc.api.qr.VerificationMode;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderId;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentUploadService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class ObjectLevelAuthorizationSecurityTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentUploadService documentUploadService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HolderCredentialService holderCredentialService;

    @Autowired
    private VerificationCapabilityService capabilityService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @Test
    @DisplayName("document QR retrieval: non-owner is rejected with 403 Forbidden")
    @WithMockUser(username = "attacker-user", roles = "HOLDER")
    void nonOwnerCannotRetrieveDocumentQr() throws Exception {
        // Upload document owned by "alice-owner"
        DocumentRecord doc = documentUploadService.upload(
                "Document for Alice".getBytes(), "alice.txt", "text/plain", "alice-owner");

        // Attacker attempts to fetch QR code for alice's document
        mockMvc.perform(get("/api/documents/" + doc.id() + "/qr"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("document QR retrieval: owner is allowed to retrieve QR code")
    @WithMockUser(username = "alice-owner", roles = "HOLDER")
    void ownerCanRetrieveDocumentQr() throws Exception {
        DocumentRecord doc = documentUploadService.upload(
                "Document for Alice 2".getBytes(), "alice2.txt", "text/plain", "alice-owner");

        mockMvc.perform(get("/api/documents/" + doc.id() + "/qr"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("document QR retrieval: admin is allowed to retrieve QR code as override")
    @WithMockUser(username = "admin-user", roles = "ADMIN")
    void adminCanRetrieveDocumentQr() throws Exception {
        DocumentRecord doc = documentUploadService.upload(
                "Document for Bob".getBytes(), "bob.txt", "text/plain", "bob-owner");

        mockMvc.perform(get("/api/documents/" + doc.id() + "/qr"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("credential issuance: issuer cannot impersonate a foreign issuerId")
    @WithMockUser(username = "malicious-issuer", roles = "ISSUER")
    void issuerCannotImpersonateForeignIssuer() throws Exception {
        String payload = """
                {
                    "schemaId": "https://schema.example.com/id-v1",
                    "issuerId": "reputable-bank-authority",
                    "attributes": [
                        {"name": "score", "type": "INTEGER", "value": 750}
                    ]
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("credential issuance: issuer can issue with their own identity")
    @WithMockUser(username = "authorized-issuer", roles = "ISSUER")
    void issuerCanIssueOwnCredential() throws Exception {
        String payload = """
                {
                    "schemaId": "https://schema.example.com/id-v1",
                    "issuerId": "authorized-issuer",
                    "attributes": [
                        {"name": "score", "type": "INTEGER", "value": 750}
                    ]
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("capability revocation: non-creator is rejected with 403 Forbidden")
    @WithMockUser(username = "eve", roles = "HOLDER")
    void nonCreatorCannotRevokeCapability() throws Exception {
        DocumentRecord doc = documentUploadService.upload("Confidential".getBytes(), "test.txt", "text/plain", "bob");
        var capability = capabilityService.mintCapability(
                null,
                doc.id(),
                VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                "bob",
                Duration.ofHours(1)
        );

        mockMvc.perform(post("/api/capabilities/" + capability.id().value() + "/revoke"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("capability revocation: creator can revoke their own capability")
    @WithMockUser(username = "bob", roles = "HOLDER")
    void creatorCanRevokeOwnCapability() throws Exception {
        DocumentRecord doc = documentUploadService.upload("Confidential 2".getBytes(), "test2.txt", "text/plain", "bob");
        var capability = capabilityService.mintCapability(
                null,
                doc.id(),
                VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                "bob",
                Duration.ofHours(1)
        );

        mockMvc.perform(post("/api/capabilities/" + capability.id().value() + "/revoke"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("capability creation: holder cannot mint capability for unowned credential")
    @WithMockUser(username = "holder-eve", roles = "HOLDER")
    void holderCannotMintCapabilityForForeignCredential() throws Exception {
        // Issue credential
        var cred = credentialService.issue(new Credential(
                "bank",
                UUID.randomUUID(),
                List.of(new CredentialAttribute("age", 25L)),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                CredentialStatus.ACTIVE
        ));

        // Provision to alice
        holderCredentialService.provision(HolderId.of("holder-alice"), CredentialId.of(cred.credential().credentialId()));

        // Eve tries to mint capability for Alice's credential
        String payload = String.format("""
                {
                    "credentialId": "%s",
                    "mode": "MODE_B_REGISTRY_CAPABILITY"
                }
                """, cred.credential().credentialId());

        mockMvc.perform(post("/api/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-obj-auth-it-").resolve("zkdoc-obj-auth.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}

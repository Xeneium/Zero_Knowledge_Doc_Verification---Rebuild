package com.zkdoc.api.publicverify;

import com.zkdoc.api.Application;
import com.zkdoc.api.qr.VerificationCapability;
import com.zkdoc.api.qr.VerificationCapabilityService;
import com.zkdoc.api.qr.VerificationMode;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialLifecycleService;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class PublicVerificationLifecycleIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private CredentialLifecycleService credentialLifecycleService;

    @Autowired
    private VerificationCapabilityService capabilityService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    private VerificationCapability createCredentialAndCapability() {
        UUID credId = UUID.randomUUID();
        Credential credential = new Credential(
                "issuer-1",
                credId,
                List.of(new CredentialAttribute("name", "Alice")),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(86400),
                CredentialStatus.ACTIVE);
        credentialService.issue(credential);

        return capabilityService.mintCapability(
                credId,
                null,
                VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                "issuer-1",
                Duration.ofHours(1)
        );
    }

    @Test
    @DisplayName("active credential resolves successfully via public QR verification")
    void activeCredentialResolvesSuccessfully() throws Exception {
        VerificationCapability cap = createCredentialAndCapability();

        mockMvc.perform(get("/api/public/verify/" + cap.capabilityToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureValid").value(true))
                .andExpect(jsonPath("$.documentActive").value(true));

        mockMvc.perform(get("/api/public/verify/capability/" + cap.capabilityToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("suspended credential rejects public verification with minimal disclosure")
    void suspendedCredentialRejectsMinimalDisclosure() throws Exception {
        VerificationCapability cap = createCredentialAndCapability();
        credentialLifecycleService.suspend(cap.credentialId(), "issuer-1", "ISSUER", "Investigation hold");

        mockMvc.perform(get("/api/public/verify/" + cap.capabilityToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureValid").value(true))
                .andExpect(jsonPath("$.documentActive").value(false));

        mockMvc.perform(get("/api/public/verify/capability/" + cap.capabilityToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("revoked credential rejects public verification with minimal disclosure")
    void revokedCredentialRejectsMinimalDisclosure() throws Exception {
        VerificationCapability cap = createCredentialAndCapability();
        credentialLifecycleService.revoke(cap.credentialId(), "issuer-1", "ISSUER", "Compromised");

        mockMvc.perform(get("/api/public/verify/" + cap.capabilityToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureValid").value(true))
                .andExpect(jsonPath("$.documentActive").value(false));

        mockMvc.perform(get("/api/public/verify/capability/" + cap.capabilityToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-pub-verify-lifecycle-it-").resolve("zkdoc-pub-lifecycle.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}

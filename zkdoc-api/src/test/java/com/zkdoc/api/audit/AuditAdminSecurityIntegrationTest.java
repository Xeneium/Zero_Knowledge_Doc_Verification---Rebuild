package com.zkdoc.api.audit;

import com.zkdoc.api.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class AuditAdminSecurityIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityAuditService auditService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @Test
    @DisplayName("Anonymous user cannot query audit events (401 Unauthorized)")
    void anonymousCannotQueryAuditEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Holder cannot query audit events (403 Forbidden)")
    @WithMockUser(username = "holder-1", roles = "HOLDER")
    void holderCannotQueryAuditEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Issuer cannot query audit events (403 Forbidden)")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void issuerCannotQueryAuditEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verifier cannot query audit events (403 Forbidden)")
    @WithMockUser(username = "verifier-1", roles = "VERIFIER")
    void verifierCannotQueryAuditEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can query audit events and results are cleanly bounded")
    @WithMockUser(username = "admin-1", roles = "ADMIN")
    void adminCanQueryAuditEvents() throws Exception {
        auditService.record(AuditEventRequest.builder(AuditEventType.ADMIN_SECURITY_OPERATION)
                .actor("admin-1", ActorType.ADMIN)
                .action("SECURITY_REVIEW")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        mockMvc.perform(get("/api/admin/audit/events?size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].eventType").exists())
                .andExpect(jsonPath("$.items[0].eventHash").exists())
                .andExpect(jsonPath("$.items[0].previousEventHash").exists());
    }

    @Test
    @DisplayName("Admin can verify cryptographic audit trail integrity")
    @WithMockUser(username = "admin-1", roles = "ADMIN")
    void adminCanVerifyAuditIntegrity() throws Exception {
        mockMvc.perform(get("/api/admin/audit/verify-integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-audit-admin-it-").resolve("zkdoc-audit.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}

package com.zkdoc.api.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkdoc.api.admin.KeyRegistryAdminController;
import com.zkdoc.api.audit.AuditAdminController;
import com.zkdoc.api.audit.JdbcSecurityAuditService;
import com.zkdoc.api.audit.SecurityAuditService;
import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.api.credential.CredentialController;
import com.zkdoc.api.document.DocumentController;
import com.zkdoc.api.document.QrCodeService;
import com.zkdoc.api.error.GlobalExceptionHandler;
import com.zkdoc.api.holder.HolderCredentialController;
import com.zkdoc.api.publicverify.PublicVerificationController;
import com.zkdoc.api.qr.JdbcVerificationCapabilityStore;
import com.zkdoc.api.qr.VerificationCapabilityService;
import com.zkdoc.api.qr.VerificationCapabilityStore;
import com.zkdoc.api.ratelimit.BucketRateLimitService;
import com.zkdoc.api.security.ObjectAuthorizationService;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialLifecycleEventStore;
import com.zkdoc.credential.CredentialLifecycleService;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.HolderCredential;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderCredentialStore;
import com.zkdoc.credential.HolderId;
import com.zkdoc.credential.JdbcCredentialLifecycleEventStore;
import com.zkdoc.credential.JdbcCredentialStore;
import com.zkdoc.credential.JdbcHolderCredentialStore;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentStore;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.JdbcDocumentStore;
import com.zkdoc.document.RsaPssSigner;
import com.zkdoc.document.Sha256Hasher;
import com.zkdoc.keys.JdbcKeyRegistry;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyRegistryService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phase 18: End-to-End Workflow Integration Tests (Issuer, Holder, Verifier, Administrator)")
class EndToEndWorkflowIntegrationTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private CredentialService credentialService;
    private HolderCredentialService holderCredentialService;
    private DocumentUploadService documentUploadService;
    private KeyRegistry keyRegistry;
    private SecurityAuditService auditService;
    private ObjectAuthorizationService objectAuthorizationService;

    // Test Principals with strict RBAC authorities
    private final Authentication issuerAuth = new UsernamePasswordAuthenticationToken(
            "acme-corp", null, List.of(new SimpleGrantedAuthority("ROLE_ISSUER")));

    private final Authentication maliciousIssuerAuth = new UsernamePasswordAuthenticationToken(
            "malicious-issuer", null, List.of(new SimpleGrantedAuthority("ROLE_ISSUER")));

    private final Authentication holderBobAuth = new UsernamePasswordAuthenticationToken(
            "holder-bob", null, List.of(new SimpleGrantedAuthority("ROLE_HOLDER")));

    private final Authentication holderCharlieAuth = new UsernamePasswordAuthenticationToken(
            "holder-charlie", null, List.of(new SimpleGrantedAuthority("ROLE_HOLDER")));

    private final Authentication verifierAuth = new UsernamePasswordAuthenticationToken(
            "verifier-corp", null, List.of(new SimpleGrantedAuthority("ROLE_VERIFIER")));

    private final Authentication adminAuth = new UsernamePasswordAuthenticationToken(
            "admin-root", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("workflow-test.db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbPath.toAbsolutePath());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/sqlite")
                .load();
        flyway.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Clock clock = Clock.systemUTC();

        DocumentStore documentStore = new JdbcDocumentStore(jdbcTemplate);
        CredentialStore credentialStore = new JdbcCredentialStore(jdbcTemplate);
        HolderCredentialStore holderCredentialStore = new JdbcHolderCredentialStore(jdbcTemplate);
        VerificationCapabilityStore capabilityStore = new JdbcVerificationCapabilityStore(jdbcTemplate);
        keyRegistry = new JdbcKeyRegistry(jdbcTemplate);
        auditService = new JdbcSecurityAuditService(jdbcTemplate, clock);
        CredentialLifecycleEventStore eventStore = new JdbcCredentialLifecycleEventStore(jdbcTemplate);

        KeyRegistryService keyRegistryService = new KeyRegistryService(keyRegistry);
        CredentialLifecycleService lifecycleService = new CredentialLifecycleService(credentialStore, eventStore, clock);
        objectAuthorizationService = new ObjectAuthorizationService(
                documentStore, credentialStore, holderCredentialStore, capabilityStore, auditService);
        VerificationAuditService verificationAuditService = new VerificationAuditService(jdbcTemplate, auditService);

        documentUploadService = new DocumentUploadService(documentStore, new Sha256Hasher(), new RsaPssSigner());
        credentialService = new CredentialService(
                credentialStore, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(),
                new CredentialRsaPssSigner(), keyRegistryService, null, clock);
        holderCredentialService = new HolderCredentialService(holderCredentialStore, credentialStore);
        QrCodeService qrCodeService = new QrCodeService();
        VerificationCapabilityService capabilityService = new VerificationCapabilityService(
                capabilityStore, qrCodeService, new ObjectMapper(), "http://localhost:8080");

        // Instantiate controllers with production-grade dependencies
        CredentialController credentialController = new CredentialController(
                credentialService, lifecycleService, credentialStore, verificationAuditService, objectAuthorizationService, null);
        HolderCredentialController holderCredentialController = new HolderCredentialController(
                holderCredentialService, verificationAuditService, null, new BucketRateLimitService(), null);
        DocumentController documentController = new DocumentController(
                documentUploadService, qrCodeService, verificationAuditService, objectAuthorizationService, null, "http://localhost:8080/api/public/verify");
        PublicVerificationController publicVerificationController = new PublicVerificationController(
                documentUploadService, verificationAuditService, capabilityService, credentialService, null);
        KeyRegistryAdminController keyRegistryAdminController = new KeyRegistryAdminController(
                keyRegistryService, keyRegistry);
        AuditAdminController auditAdminController = new AuditAdminController(auditService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        credentialController,
                        holderCredentialController,
                        documentController,
                        publicVerificationController,
                        keyRegistryAdminController,
                        auditAdminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==========================================
    // 1. ISSUER WORKFLOW TESTS
    // ==========================================

    @Test
    @DisplayName("Issuer issues credential and uploads document successfully")
    void testAuthorizedIssuerWorkflow() throws Exception {
        String issuePayload = """
                {
                    "issuerId": "acme-corp",
                    "attributes": [
                        {"name": "employeeId", "value": 1042, "type": "INTEGER"},
                        {"name": "securityClearance", "value": 3, "type": "INTEGER"}
                    ]
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .principal(issuerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialId", notNullValue()))
                .andExpect(jsonPath("$.digest", notNullValue()));

        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", "%PDF-1.4\nConfidential policy".getBytes());
        mockMvc.perform(multipart("/api/documents").file(file).principal(issuerAuth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.digest", notNullValue()));
    }

    @Test
    @DisplayName("Issuer cannot issue credential impersonating another issuer")
    void testIssuerImpersonationBlocked() throws Exception {
        String issuePayload = """
                {
                    "issuerId": "gov-authority",
                    "attributes": [
                        {"name": "citizenship", "value": 1, "type": "INTEGER"}
                    ]
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .principal(maliciousIssuerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("Non-issuer role (e.g. HOLDER) is strictly forbidden from issuance")
    void testNonIssuerRoleBlockedFromIssuance() throws Exception {
        String issuePayload = """
                {
                    "issuerId": "holder-bob",
                    "attributes": [{"name": "score", "value": 100, "type": "INTEGER"}]
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .principal(holderBobAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 2. HOLDER WORKFLOW TESTS
    // ==========================================

    @Test
    @DisplayName("Holder provisions credential, lists own credentials, and accesses details")
    void testHolderWorkflowLifecycle() throws Exception {
        var cred = credentialService.issue(new Credential(
                "univ",
                UUID.randomUUID(),
                List.of(new CredentialAttribute("gpa", 380L)),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                CredentialStatus.ACTIVE
        ));

        String provPayload = String.format("{\"credentialId\": \"%s\"}", cred.credential().credentialId());
        mockMvc.perform(post("/api/holder/credentials/provision")
                        .principal(holderBobAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holderId", is("holder-bob")))
                .andExpect(jsonPath("$.credentialId", is(cred.credential().credentialId().toString())));

        mockMvc.perform(get("/api/holder/credentials")
                        .principal(holderBobAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].holderId", is("holder-bob")));
    }

    @Test
    @DisplayName("Holder cannot access another holder's provisioned credential (IDOR protection)")
    void testHolderCrossAccessBlocked() throws Exception {
        var cred = credentialService.issue(new Credential(
                "univ",
                UUID.randomUUID(),
                List.of(new CredentialAttribute("score", 95L)),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                CredentialStatus.ACTIVE
        ));

        HolderCredential hc = holderCredentialService.provision(
                HolderId.of("holder-david"), CredentialId.of(cred.credential().credentialId()));

        mockMvc.perform(get("/api/holder/credentials/" + hc.id().value())
                        .principal(holderCharlieAuth))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // 3. VERIFIER WORKFLOW TESTS
    // ==========================================

    @Test
    @DisplayName("Verifier can verify authentic credential and public verification capability")
    void testVerifierAuthenticCredential() throws Exception {
        var cred = credentialService.issue(new Credential(
                "issuer-bank",
                UUID.randomUUID(),
                List.of(new CredentialAttribute("balance", 5000L)),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                CredentialStatus.ACTIVE
        ));

        mockMvc.perform(get("/api/credentials/" + cred.credential().credentialId() + "/verify")
                        .principal(verifierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified", is(true)))
                .andExpect(jsonPath("$.signatureValid", is(true)))
                .andExpect(jsonPath("$.operationalStatusValid", is(true)));
    }

    @Test
    @DisplayName("Anonymous public verification endpoint verifies token without leaking secrets")
    void testAnonymousPublicVerification() throws Exception {
        DocumentRecord doc = documentUploadService.upload("Public Doc".getBytes(), "pub.txt", "text/plain", "system");

        mockMvc.perform(get("/api/public/verify/" + doc.publicToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureValid", is(true)))
                .andExpect(jsonPath("$.documentActive", is(true)));
    }

    // ==========================================
    // 4. ADMINISTRATOR WORKFLOW TESTS
    // ==========================================

    @Test
    @DisplayName("Admin can inspect security audit trail and key registry")
    void testAdminAuditAndKeyInspection() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events")
                        .principal(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", notNullValue()));

        mockMvc.perform(get("/api/admin/keys")
                        .principal(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("Object-level authorization service strictly enforces RBAC and IDOR policies")
    void testObjectLevelAuthorizationPolicies() {
        // 1. Non-admin or non-issuer cannot issue
        assertThrows(AccessDeniedException.class, () ->
                objectAuthorizationService.checkCredentialIssuance(holderBobAuth, "acme-corp"));

        // 2. Issuer cannot impersonate another issuer
        assertThrows(AccessDeniedException.class, () ->
                objectAuthorizationService.checkCredentialIssuance(maliciousIssuerAuth, "gov-authority"));

        // 3. Holder cannot access other holder's credential
        assertThrows(AccessDeniedException.class, () ->
                objectAuthorizationService.checkCredentialLifecycleAccess(holderCharlieAuth, UUID.randomUUID()));
    }
}

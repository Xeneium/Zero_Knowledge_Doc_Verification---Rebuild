package com.zkdoc.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.zkdoc.api.Application;
import com.zkdoc.api.audit.AuditEventRequest;
import com.zkdoc.api.audit.AuditEventType;
import com.zkdoc.api.audit.AuditIntegrityResult;
import com.zkdoc.api.audit.AuditOutcome;
import com.zkdoc.api.audit.JdbcSecurityAuditService;
import com.zkdoc.api.ratelimit.BucketRateLimitService;
import com.zkdoc.api.ratelimit.RateLimitCategory;
import com.zkdoc.api.ratelimit.RateLimitDecision;
import com.zkdoc.api.ratelimit.RateLimitKey;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderId;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.storage.DocumentContentValidator;
import com.zkdoc.document.storage.FilesystemDocumentArtifactStorage;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 23 Full Security & Adversarial Validation Suite.
 * Validates actual runtime security properties, adversarial defenses, and isolation boundaries:
 * 1. RFC 8725 JWT Hardening (alg="none", algorithm confusion, expired tokens, key-id validation, corrupted signatures)
 * 2. Object-Level Authorization & IDOR/BOLA Prevention (holder isolation, document QR boundary, admin role enforcement)
 * 3. Document Storage & Path Traversal Protections (directory escape, null bytes, MIME magic byte verification)
 * 4. Tamper-Evident Audit Chain Integrity (sequential hash chain verification, detection of modified intermediate events)
 * 5. Rate Limiting & Resource Abuse Resistance (concurrency bounds, rapid burst throttling, Retry-After header)
 * 6. Information Leakage & RFC 9457 Error Sanitization (malformed requests do not leak stack traces or schema details)
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase23AdversarialSecurityValidationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();
    private static final Path STORAGE_DIR = newTempDir("zkdoc-p23-storage-");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeyRegistry keyRegistry;

    @Autowired
    private DocumentUploadService documentUploadService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HolderCredentialService holderCredentialService;

    @Autowired
    private BucketRateLimitService rateLimitService;

    @Autowired
    private com.zkdoc.api.audit.SecurityAuditService securityAuditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private KeyPair validRsaKeyPair;
    private String validKeyId;
    private final String expectedIssuer = "https://auth.zkdoc.example.com";
    private final String expectedAudience = "zkdoc-platform-api";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
        registry.add("zkdoc.storage.base-dir", () -> STORAGE_DIR.toAbsolutePath().toString());
        registry.add("zkdoc.security.jwt.issuer", () -> "https://auth.zkdoc.example.com");
        registry.add("zkdoc.security.jwt.audience", () -> "zkdoc-platform-api");
        registry.add("zkdoc.security.jwt.clock-skew-seconds", () -> "30");
    }

    @BeforeAll
    void setupKeys() {
        validRsaKeyPair = JwtTokenTestHelper.generateRsaKeyPair();
        validKeyId = "phase23-jwt-key-" + UUID.randomUUID();

        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(validRsaKeyPair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        RegisteredKey regKey = new RegisteredKey(
                UUID.randomUUID(),
                validKeyId,
                KeyPurpose.JWT_SIGNING,
                KeyStatus.ACTIVE,
                "RSA",
                pem,
                "phase23-test"
        );

        keyRegistry.register(regKey);
    }

    // =========================================================================
    // 1. JWT ADVERSARIAL ATTACK SUITE (RFC 8725)
    // =========================================================================

    @Test
    @DisplayName("JWT Attack 1: Rejects algorithm 'none' (unsigned JWT bypass attempt)")
    void attack_jwt_algorithmNone_rejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(expectedIssuer)
                .audience(expectedAudience)
                .subject("malicious-admin")
                .claim("roles", List.of("ADMIN"))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();

        PlainJWT plainJWT = new PlainJWT(claims);
        String noneToken = plainJWT.serialize();

        mockMvc.perform(get("/api/admin/metrics")
                        .header("Authorization", "Bearer " + noneToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT Attack 2: Rejects HMAC-SHA256 algorithm confusion (using RSA public key as HMAC secret)")
    void attack_jwt_hmacAlgorithmConfusion_rejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(expectedIssuer)
                .audience(expectedAudience)
                .subject("attacker")
                .claim("roles", List.of("ADMIN"))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID(validKeyId)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new MACSigner(validRsaKeyPair.getPublic().getEncoded()));
        String confusionToken = signedJWT.serialize();

        mockMvc.perform(get("/api/admin/metrics")
                        .header("Authorization", "Bearer " + confusionToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT Attack 3: Rejects expired token")
    void attack_jwt_expiredToken_rejected() throws Exception {
        String expiredToken = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "expired-user",
                List.of("HOLDER"),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT Attack 4: Rejects token signed with unknown / unregistered key ID")
    void attack_jwt_unknownKeyId_rejected() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                "completely-unknown-key-id",
                expectedIssuer,
                expectedAudience,
                "holder-user",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT Attack 5: Rejects token with modified / tampered signature bytes")
    void attack_jwt_tamperedSignature_rejected() throws Exception {
        String validToken = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "holder-user",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 4) + "AAAA";

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. OBJECT-LEVEL AUTHORIZATION & IDOR/BOLA DEFENSES
    // =========================================================================

    @Test
    @DisplayName("IDOR Defense 1: Holder B cannot access or view Holder A's credentials")
    @WithMockUser(username = "holder-bob", roles = "HOLDER")
    void idor_holderCannotAccessOtherHolderCredential() throws Exception {
        UUID credUuid = UUID.randomUUID();
        var issued = credentialService.issue(new Credential(
                "bank-authority",
                credUuid,
                List.of(new CredentialAttribute("age", 25L)),
                Instant.now(),
                Instant.now().plus(Duration.ofDays(365)),
                CredentialStatus.ACTIVE
        ));

        holderCredentialService.provision(HolderId.of("holder-alice"), CredentialId.of(credUuid));

        // Bob queries alice's credential ID directly via Holder API
        mockMvc.perform(get("/api/holder/credentials/" + credUuid))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("IDOR Defense 2: Non-owner cannot retrieve document verification QR code")
    @WithMockUser(username = "attacker-user", roles = "HOLDER")
    void idor_nonOwnerCannotRetrieveDocumentQr() throws Exception {
        DocumentRecord doc = documentUploadService.upload(
                "Confidential Salary Record".getBytes(StandardCharsets.UTF_8),
                "salary.txt",
                "text/plain",
                "victim-user"
        );

        mockMvc.perform(get("/api/documents/" + doc.id() + "/qr"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Defense: Regular HOLDER user cannot invoke ADMIN key registry or recovery endpoints")
    @WithMockUser(username = "holder-charlie", roles = "HOLDER")
    void rbac_holderCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/keys/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyId\":\"rogue-key\"}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. STORAGE & PATH TRAVERSAL DEFENSES
    // =========================================================================

    @Test
    @DisplayName("Storage Security 1: Rejects path traversal sequences (../, ..\\) in storage keys")
    void storage_pathTraversal_throwsSecurityException() {
        FilesystemDocumentArtifactStorage storage = new FilesystemDocumentArtifactStorage(STORAGE_DIR);

        assertThatThrownBy(() -> storage.resolveSecurePath("../../../etc/shadow"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected");

        assertThatThrownBy(() -> storage.resolveSecurePath("..\\..\\windows\\system32\\cmd.exe"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected");
    }

    @Test
    @DisplayName("Storage Security 2: Rejects null-byte injection in storage keys")
    void storage_nullByteInjection_throwsSecurityException() {
        FilesystemDocumentArtifactStorage storage = new FilesystemDocumentArtifactStorage(STORAGE_DIR);

        assertThatThrownBy(() -> storage.resolveSecurePath("artifacts/legit.pdf\0../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Null byte detected");
    }

    @Test
    @DisplayName("Storage Security 3: DocumentContentValidator rejects binary executable disguised as text/plain")
    void document_contentValidator_rejectsExecutableHeader() {
        byte[] elfBytes = new byte[] {0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00};
        assertThatThrownBy(() -> DocumentContentValidator.validateContent(
                new ByteArrayInputStream(elfBytes), "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Binary or executable content rejected");

        byte[] mzBytes = new byte[] {'M', 'Z', 0x00, 0x00};
        assertThatThrownBy(() -> DocumentContentValidator.validateContent(
                new ByteArrayInputStream(mzBytes), "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Binary or executable content rejected");
    }

    // =========================================================================
    // 4. TAMPER-EVIDENT AUDIT CHAIN INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Audit Defense: Tampered intermediate event sequence is immediately detected")
    void audit_tamperedSequenceDetectedByHashChain() {
        var event1 = securityAuditService.record(AuditEventRequest.builder(AuditEventType.AUTHENTICATION_SUCCESS)
                .actor("user-1", com.zkdoc.api.audit.ActorType.USER)
                .action("LOGIN")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        securityAuditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                .actor("issuer-1", com.zkdoc.api.audit.ActorType.ISSUER)
                .target(com.zkdoc.api.audit.TargetType.CREDENTIAL, UUID.randomUUID())
                .action("ISSUE")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        // Verify valid before tampering
        AuditIntegrityResult validResult = securityAuditService.verifyChainIntegrity();
        assertThat(validResult.valid()).isTrue();

        long tamperedSeq = event1.sequenceNumber();

        // Adversary directly tampers with event action in the database
        jdbcTemplate.update("UPDATE security_audit_events SET action = 'MALICIOUS_TAMPER' WHERE sequence_number = ?", tamperedSeq);

        // Verification must fail and report the exact tampered sequence number
        AuditIntegrityResult tamperedResult = securityAuditService.verifyChainIntegrity();
        assertThat(tamperedResult.valid()).isFalse();
        assertThat(tamperedResult.tamperedSequence()).isEqualTo(tamperedSeq);

        // Restore clean state for subsequent tests
        jdbcTemplate.update("UPDATE security_audit_events SET action = 'LOGIN' WHERE sequence_number = ?", tamperedSeq);
    }

    // =========================================================================
    // 5. RESOURCE LIMITS & RATE LIMITING SATURATION
    // =========================================================================

    @Test
    @DisplayName("Rate Limiting: Rapid token consumption exhausts capacity and enforces 429 Too Many Requests")
    void rateLimiting_capacityExhaustionEnforces429() throws Exception {
        rateLimitService.clear();
        String attackerIp = "203.0.113.199";
        RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "ip:" + attackerIp);
        int capacity = RateLimitCategory.PUBLIC_VERIFICATION.getCapacity();

        // Exhaust tokens
        for (int i = 0; i < capacity; i++) {
            RateLimitDecision decision = rateLimitService.tryAcquire(key);
            assertThat(decision.allowed()).isTrue();
        }

        // Next request via HTTP filter must return 429 Too Many Requests with Retry-After header
        mockMvc.perform(get("/api/public/verify/token-xyz")
                        .with(req -> {
                            req.setRemoteAddr(attackerIp);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.category").value("PUBLIC_VERIFICATION"));
    }

    // =========================================================================
    // 6. ERROR SANITIZATION & LEAKAGE PREVENTION (RFC 9457)
    // =========================================================================

    @Test
    @DisplayName("Sanitization: Malformed JSON request produces structured RFC 9457 error without stack trace")
    @WithMockUser(username = "issuer-1", roles = "ISSUER")
    void error_malformedJsonSanitizedWithoutStackTrace() throws Exception {
        mockMvc.perform(post("/api/credentials/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corruptedJson\": [}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Malformed JSON or invalid request payload"))
                // Ensure no Java stack trace, exception classes, or SQL tokens are leaked
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.message").value(not(containsString("com.fasterxml.jackson"))));
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-p23-it-").resolve("zkdoc-p23.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }

    private static Path newTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create temp directory", exception);
        }
    }
}

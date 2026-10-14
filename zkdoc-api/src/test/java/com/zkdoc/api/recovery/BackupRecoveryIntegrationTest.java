package com.zkdoc.api.recovery;

import com.zkdoc.api.audit.AuditEventCategory;
import com.zkdoc.api.audit.AuditEventRequest;
import com.zkdoc.api.audit.AuditEventType;
import com.zkdoc.api.audit.AuditOutcome;
import com.zkdoc.api.audit.JdbcSecurityAuditService;
import com.zkdoc.api.audit.SecurityAuditService;
import com.zkdoc.core.crypto.RsaPssSignatureService;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialTrustService;
import com.zkdoc.credential.JdbcCredentialStore;
import com.zkdoc.document.ArtifactId;
import com.zkdoc.document.ArtifactStatus;
import com.zkdoc.document.DocumentArtifact;
import com.zkdoc.document.DocumentId;
import com.zkdoc.document.JdbcArtifactStore;
import com.zkdoc.document.storage.FilesystemDocumentArtifactStorage;
import com.zkdoc.keys.DevelopmentSigningKeyProvider;
import com.zkdoc.keys.JdbcKeyRegistry;
import com.zkdoc.keys.KeyAlgorithmPolicy;
import com.zkdoc.keys.KeyFingerprint;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 22: Backup, Recovery & Post-Restoration Reconciliation Integration Tests")
class BackupRecoveryIntegrationTest {

    @TempDir
    Path tempStorageDir;

    private JdbcTemplate jdbcTemplate;
    private FilesystemDocumentArtifactStorage artifactStorage;
    private JdbcKeyRegistry keyRegistry;
    private JdbcArtifactStore artifactStore;
    private JdbcCredentialStore credentialStore;
    private CredentialTrustService credentialTrustService;
    private SecurityAuditService auditService;
    private BackupRecoveryValidator validator;

    private KeyPair testKeyPair;
    private String testPublicKeyPem;
    private String testKeyId;

    @BeforeEach
    void setUp() throws Exception {
        // Use an isolated in-memory SQLite database with single connection persistence
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Initialize schema tables required for recovery testing
        initSchema(jdbcTemplate);

        artifactStorage = new FilesystemDocumentArtifactStorage(tempStorageDir);
        keyRegistry = new JdbcKeyRegistry(jdbcTemplate);
        artifactStore = new JdbcArtifactStore(jdbcTemplate);
        credentialStore = new JdbcCredentialStore(jdbcTemplate);

        // Generate standard test RSA-PSS keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();
        testPublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(testKeyPair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----";
        testKeyId = "issuer-key-" + UUID.randomUUID();

        // Register key
        RegisteredKey regKey = new RegisteredKey(
                UUID.randomUUID(),
                testKeyId,
                KeyPurpose.CREDENTIAL_SIGNING,
                KeyStatus.ACTIVE,
                "RSASSA-PSS",
                KeyAlgorithmPolicy.MANDATED_PARAMETERS,
                "issuer-dept-1",
                KeyOwnerType.ISSUER,
                testPublicKeyPem,
                testKeyPair.getPublic().getEncoded(),
                "X.509/PEM",
                KeyFingerprint.fromPublicKey(testKeyPair.getPublic()).value(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                1,
                Collections.emptyMap()
        );
        keyRegistry.register(regKey);

        DevelopmentSigningKeyProvider keyProvider = new DevelopmentSigningKeyProvider();
        keyProvider.registerKey(testKeyId, KeyPurpose.CREDENTIAL_SIGNING, testKeyPair.getPrivate(), testKeyPair.getPublic());

        CredentialSha256Hasher credHasher = new CredentialSha256Hasher();
        CredentialRsaPssSigner credSigner = new CredentialRsaPssSigner(testKeyPair);
        credentialTrustService = new CredentialTrustService(credentialStore, credHasher, credSigner);

        auditService = new JdbcSecurityAuditService(jdbcTemplate, Clock.systemUTC());
        validator = new BackupRecoveryValidator(jdbcTemplate, artifactStorage, keyRegistry, credentialTrustService, auditService);
    }

    @Test
    @DisplayName("Complete clean recovery reconciliation produces HEALTHY status and unbroken chain")
    void cleanRecoveryReconciliationSucceeds() throws Exception {
        // 1. Store artifact
        String storageKey = "artifacts/docs/salary-slips.pdf";
        byte[] content = "%PDF-1.7 Valid Restored Document Bytes".getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);

        artifactStorage.store(storageKey, new ByteArrayInputStream(content), content.length);

        UUID docId = UUID.randomUUID();
        DocumentArtifact artifact = new DocumentArtifact(
                ArtifactId.of(UUID.randomUUID()),
                DocumentId.of(docId),
                1,
                "application/pdf",
                content.length,
                com.zkdoc.core.crypto.Digest.of(digest),
                storageKey,
                ArtifactStatus.ACTIVE,
                Instant.now()
        );
        artifactStore.save(artifact);

        // 2. Store verifiable credential directly as restored in database
        UUID credId = UUID.randomUUID();
        byte[] payload = ("{\"sub\":\"emp100\",\"salary\":75000}").getBytes(StandardCharsets.UTF_8);
        byte[] credDigest = MessageDigest.getInstance("SHA-256").digest(payload);
        byte[] credSig = new RsaPssSignatureService().signRsaPss(credDigest, testKeyPair.getPrivate());

        jdbcTemplate.update("""
                INSERT INTO credentials (
                    credential_id, issuer_id, issued_at, expires_at, status, canonical_payload, digest, signature, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """,
                credId.toString(),
                "issuer-dept-1",
                Instant.now().toString(),
                Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "ACTIVE",
                payload,
                credDigest,
                credSig
        );

        // 3. Record audit trail events
        auditService.record(AuditEventRequest.builder(AuditEventType.ADMIN_SECURITY_OPERATION)
                .actor("system-admin", com.zkdoc.api.audit.ActorType.SYSTEM)
                .target(com.zkdoc.api.audit.TargetType.SYSTEM, "backup-001")
                .action("BACKUP")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("OK")
                .correlationId("corr-1")
                .clientIp("127.0.0.1")
                .metadataJson("{\"size\":1024}")
                .build());

        // 4. Run reconciliation
        RecoveryReconciliationReport report = validator.reconcileAll();

        assertNotNull(report);
        assertEquals(RecoveryStatus.HEALTHY, report.status());
        assertTrue(report.isFullyRecovered());
        assertEquals(1, report.totalArtifactsChecked());
        assertEquals(1, report.validArtifacts());
        assertTrue(report.missingArtifacts().isEmpty());
        assertTrue(report.corruptedArtifacts().isEmpty());
        assertEquals(1, report.totalKeysChecked());
        assertEquals(1, report.activeKeys());
        assertEquals(1, report.totalCredentialsChecked());
        assertEquals(1, report.validCredentials());
        assertTrue(report.auditChainValid());
        assertEquals(1, report.totalAuditEvents());
    }

    @Test
    @DisplayName("Detect corrupted artifact file when disk content differs from database digest")
    void detectsCorruptedArtifactOnDisk() throws Exception {
        String storageKey = "artifacts/contracts/nda.pdf";
        byte[] content = "%PDF-1.7 Real NDA Content".getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);

        artifactStorage.store(storageKey, new ByteArrayInputStream(content), content.length);

        DocumentArtifact artifact = new DocumentArtifact(
                ArtifactId.of(UUID.randomUUID()),
                DocumentId.of(UUID.randomUUID()),
                1,
                "application/pdf",
                content.length,
                com.zkdoc.core.crypto.Digest.of(digest),
                storageKey,
                ArtifactStatus.ACTIVE,
                Instant.now()
        );
        artifactStore.save(artifact);

        // Adversary or storage silent corruption modifies disk content
        Path physicalPath = artifactStorage.resolveSecurePath(storageKey);
        Files.writeString(physicalPath, "TAMPERED BY ATTACKER OR CORRUPTED SECTOR");

        RecoveryReconciliationReport report = validator.reconcileAll();

        assertEquals(RecoveryStatus.CORRUPTED, report.status());
        assertFalse(report.isFullyRecovered());
        assertEquals(1, report.totalArtifactsChecked());
        assertEquals(0, report.validArtifacts());
        assertEquals(1, report.corruptedArtifacts().size());
        assertTrue(report.corruptedArtifacts().get(0).contains("expected="));
    }

    @Test
    @DisplayName("Detect missing artifact when database has record but file was lost")
    void detectsMissingArtifactFile() {
        DocumentArtifact artifact = new DocumentArtifact(
                ArtifactId.of(UUID.randomUUID()),
                DocumentId.of(UUID.randomUUID()),
                1,
                "application/pdf",
                1024,
                com.zkdoc.core.crypto.Digest.of(new byte[32]),
                "artifacts/missing/deleted.pdf",
                ArtifactStatus.ACTIVE,
                Instant.now()
        );
        artifactStore.save(artifact);

        RecoveryReconciliationReport report = validator.reconcileAll();

        assertEquals(RecoveryStatus.WARNING, report.status());
        assertFalse(report.isFullyRecovered());
        assertEquals(1, report.missingArtifacts().size());
        assertTrue(report.missingArtifacts().get(0).contains("artifacts/missing/deleted.pdf"));
    }

    @Test
    @DisplayName("Detect broken security audit hash chain after tampering")
    void detectsBrokenSecurityAuditHashChain() {
        // Record two legitimate events
        auditService.record(AuditEventRequest.builder(AuditEventType.KEY_REGISTERED)
                .actor("system", com.zkdoc.api.audit.ActorType.SYSTEM)
                .action("START")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("OK")
                .correlationId("corr-10")
                .clientIp("127.0.0.1")
                .build());
        auditService.record(AuditEventRequest.builder(AuditEventType.DOCUMENT_UPLOADED)
                .actor("holder-1", com.zkdoc.api.audit.ActorType.USER)
                .target(com.zkdoc.api.audit.TargetType.DOCUMENT, "doc-99")
                .action("UPLOAD")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("OK")
                .correlationId("corr-11")
                .clientIp("127.0.0.1")
                .build());

        // Tamper directly with the database audit record event_hash
        jdbcTemplate.update("UPDATE security_audit_events SET event_hash = '000000000000000000000000000000000000000000000000000000000000dead' WHERE sequence_number = 1");

        RecoveryReconciliationReport report = validator.reconcileAll();

        assertEquals(RecoveryStatus.CORRUPTED, report.status());
        assertFalse(report.auditChainValid());
        assertNotNull(report.auditFailureReason());
        assertTrue(report.auditFailureReason().contains("Integrity hash mismatch") || report.auditFailureReason().contains("Sequence break"));
    }

    @Test
    @DisplayName("Private key non-export invariant: private signing keys never present in DB or artifact backups")
    void privateKeyNonExportInvariant() {
        // Verify key_registry does not contain private key columns or private key PEM
        List<String> pems = jdbcTemplate.query("SELECT public_key_pem FROM key_registry",
                (rs, rowNum) -> rs.getString("public_key_pem"));

        for (String pem : pems) {
            assertFalse(pem.contains("BEGIN PRIVATE KEY"), "Database must NEVER contain private key headers");
            assertFalse(pem.contains("BEGIN RSA PRIVATE KEY"), "Database must NEVER contain RSA private key headers");
            assertTrue(pem.contains("BEGIN PUBLIC KEY"), "Only public keys should be stored");
        }
    }

    private void initSchema(JdbcTemplate jt) {
        jt.execute("""
                CREATE TABLE IF NOT EXISTS key_registry (
                    id VARCHAR(36) PRIMARY KEY,
                    key_id VARCHAR(64) UNIQUE NOT NULL,
                    owner_id VARCHAR(128) NOT NULL,
                    owner_type VARCHAR(32) NOT NULL,
                    purpose VARCHAR(32) NOT NULL,
                    algorithm VARCHAR(32) NOT NULL,
                    algorithm_parameters VARCHAR(256),
                    public_key_pem TEXT NOT NULL,
                    public_key_der BLOB,
                    public_key_encoding VARCHAR(32),
                    fingerprint VARCHAR(64),
                    status VARCHAR(16) NOT NULL,
                    created_at VARCHAR(64) NOT NULL,
                    activated_at VARCHAR(64),
                    retired_at VARCHAR(64),
                    revoked_at VARCHAR(64),
                    revocation_reason TEXT,
                    replacement_key_id VARCHAR(64),
                    version INTEGER DEFAULT 1
                );
                """);

        jt.execute("""
                CREATE TABLE IF NOT EXISTS document_artifacts (
                    id VARCHAR(36) PRIMARY KEY,
                    document_id VARCHAR(36) NOT NULL,
                    artifact_version INTEGER NOT NULL,
                    content_type VARCHAR(100) NOT NULL,
                    file_size BIGINT NOT NULL,
                    sha256_digest VARCHAR(64) NOT NULL,
                    storage_key VARCHAR(512) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at VARCHAR(64) NOT NULL
                );
                """);

        jt.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id VARCHAR(36) PRIMARY KEY,
                    content BLOB,
                    digest BLOB,
                    signature BLOB,
                    public_token VARCHAR(64) UNIQUE,
                    active BOOLEAN,
                    owner_id VARCHAR(128)
                );
                """);

        jt.execute("""
                CREATE TABLE IF NOT EXISTS credentials (
                    credential_id VARCHAR(36) PRIMARY KEY,
                    issuer_id VARCHAR(128) NOT NULL,
                    issued_at VARCHAR(64) NOT NULL,
                    expires_at VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    canonical_payload BLOB NOT NULL,
                    digest BLOB NOT NULL,
                    signature BLOB NOT NULL,
                    suspended_at VARCHAR(64),
                    suspension_reason TEXT,
                    revoked_at VARCHAR(64),
                    revocation_reason TEXT,
                    version INTEGER DEFAULT 1
                );
                """);

        jt.execute("""
                CREATE TABLE IF NOT EXISTS security_audit_events (
                    id VARCHAR(36) PRIMARY KEY,
                    sequence_number INTEGER NOT NULL UNIQUE,
                    event_type VARCHAR(64) NOT NULL,
                    event_category VARCHAR(32) NOT NULL,
                    timestamp VARCHAR(64) NOT NULL,
                    actor_subject VARCHAR(128) NOT NULL,
                    actor_type VARCHAR(32) NOT NULL,
                    target_type VARCHAR(64),
                    target_id VARCHAR(128),
                    action VARCHAR(64) NOT NULL,
                    outcome VARCHAR(16) NOT NULL,
                    reason_code VARCHAR(64),
                    correlation_id VARCHAR(64) NOT NULL,
                    client_ip_hash VARCHAR(64) NOT NULL,
                    metadata_json VARCHAR(1024),
                    previous_event_hash VARCHAR(64) NOT NULL,
                    event_hash VARCHAR(64) NOT NULL
                );
                """);
    }
}

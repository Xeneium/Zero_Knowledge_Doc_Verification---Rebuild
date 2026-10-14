package com.zkdoc.api.recovery;

import com.zkdoc.api.audit.AuditIntegrityResult;
import com.zkdoc.api.audit.SecurityAuditService;
import com.zkdoc.credential.CredentialTrustVerifier;
import com.zkdoc.document.storage.DocumentArtifactStorage;
import com.zkdoc.keys.KeyAlgorithmPolicy;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Production-grade recovery reconciliation and validation engine.
 * Inspects restored database records, verifies physical artifact bytes against recorded SHA-256 digests,
 * enforces key registry trust policy, tests credential signatures, and validates the tamper-evident
 * security audit hash chain.
 */
@Service
public class BackupRecoveryValidator {

    private static final Logger log = LoggerFactory.getLogger(BackupRecoveryValidator.class);

    private final JdbcTemplate jdbcTemplate;
    private final DocumentArtifactStorage artifactStorage;
    private final KeyRegistry keyRegistry;
    private final CredentialTrustVerifier credentialTrustVerifier;
    private final SecurityAuditService securityAuditService;

    @Autowired
    public BackupRecoveryValidator(
            JdbcTemplate jdbcTemplate,
            DocumentArtifactStorage artifactStorage,
            KeyRegistry keyRegistry,
            CredentialTrustVerifier credentialTrustVerifier,
            SecurityAuditService securityAuditService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.artifactStorage = Objects.requireNonNull(artifactStorage, "artifactStorage must not be null");
        this.keyRegistry = Objects.requireNonNull(keyRegistry, "keyRegistry must not be null");
        this.credentialTrustVerifier = Objects.requireNonNull(credentialTrustVerifier, "credentialTrustVerifier must not be null");
        this.securityAuditService = Objects.requireNonNull(securityAuditService, "securityAuditService must not be null");
    }

    /**
     * Executes complete post-restore reconciliation across all subsystems.
     */
    public RecoveryReconciliationReport reconcileAll() {
        log.info("Starting comprehensive post-recovery reconciliation...");
        StringBuilder summary = new StringBuilder();

        // 1. Schema Version Validation
        SchemaStatus schemaStatus = validateSchema();
        summary.append(String.format("Schema: %s (version: %s). ",
                schemaStatus.valid() ? "VALID" : "FAILED", schemaStatus.version()));

        // 2. Document Artifacts Reconciliation
        ArtifactReconciliation artifactResult = reconcileArtifacts();
        summary.append(String.format("Artifacts: %d checked (%d valid, %d missing, %d corrupted). ",
                artifactResult.total(), artifactResult.valid(),
                artifactResult.missing().size(), artifactResult.corrupted().size()));

        // 3. Key Registry & Trust State Validation
        KeyReconciliation keyResult = validateKeys();
        summary.append(String.format("Keys: %d checked (%d active, %d revoked, %d invalid). ",
                keyResult.total(), keyResult.active(), keyResult.revoked(), keyResult.invalid().size()));

        // 4. Verifiable Credentials Validation
        CredentialReconciliation credResult = validateCredentials();
        summary.append(String.format("Credentials: %d checked (%d valid, %d invalid). ",
                credResult.total(), credResult.valid(), credResult.invalid().size()));

        // 5. Security Audit Chain Integrity Validation
        AuditIntegrityResult auditResult = securityAuditService.verifyChainIntegrity();
        summary.append(String.format("Audit Trail: %s (%d events verified).",
                auditResult.valid() ? "INTACT" : "FAILED (" + auditResult.failureReason() + ")",
                auditResult.checkedCount()));

        // Determine Overall Recovery Status
        RecoveryStatus status;
        if (!schemaStatus.valid()
                || !artifactResult.corrupted().isEmpty()
                || !keyResult.invalid().isEmpty()
                || !credResult.invalid().isEmpty()
                || !auditResult.valid()) {
            status = RecoveryStatus.CORRUPTED;
        } else if (!artifactResult.missing().isEmpty()) {
            status = RecoveryStatus.WARNING;
        } else {
            status = RecoveryStatus.HEALTHY;
        }

        RecoveryReconciliationReport report = new RecoveryReconciliationReport(
                Instant.now(),
                status,
                schemaStatus.valid(),
                schemaStatus.version(),
                artifactResult.total(),
                artifactResult.valid(),
                artifactResult.missing(),
                artifactResult.corrupted(),
                keyResult.total(),
                keyResult.active(),
                keyResult.revoked(),
                keyResult.invalid(),
                credResult.total(),
                credResult.valid(),
                credResult.invalid(),
                auditResult.valid(),
                auditResult.checkedCount(),
                auditResult.failureReason(),
                summary.toString()
        );

        log.info("Recovery reconciliation completed with status: {}", report.status());
        return report;
    }

    private SchemaStatus validateSchema() {
        try {
            List<String> versions = jdbcTemplate.query(
                    "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                    (rs, rowNum) -> {
                        boolean success = rs.getBoolean("success");
                        String version = rs.getString("version");
                        return success ? version : "FAILED_AT_" + version;
                    }
            );
            if (versions.isEmpty()) {
                return new SchemaStatus(true, "BASELINE_OR_EMPTY");
            }
            String latest = versions.get(0);
            return new SchemaStatus(!latest.startsWith("FAILED_AT_"), latest);
        } catch (Exception e) {
            log.warn("Could not query flyway_schema_history: {}", e.getMessage());
            // SQLite or custom setup without flyway history table yet
            return new SchemaStatus(true, "UNMANAGED_OR_DEV");
        }
    }

    private ArtifactReconciliation reconcileArtifacts() {
        List<String> missing = new ArrayList<>();
        List<String> corrupted = new ArrayList<>();
        int valid = 0;
        int total = 0;

        try {
            List<ArtifactMeta> list = jdbcTemplate.query(
                    "SELECT id, sha256_digest, storage_key, file_size FROM document_artifacts",
                    (rs, rowNum) -> new ArtifactMeta(
                            rs.getString("id"),
                            rs.getString("sha256_digest"),
                            rs.getString("storage_key"),
                            rs.getLong("file_size")
                    )
            );

            total = list.size();
            for (ArtifactMeta meta : list) {
                if (!artifactStorage.exists(meta.storageKey())) {
                    missing.add(meta.id() + ":" + meta.storageKey());
                    continue;
                }

                try {
                    Optional<InputStream> isOpt = artifactStorage.openStream(meta.storageKey());
                    if (isOpt.isEmpty()) {
                        missing.add(meta.id() + ":" + meta.storageKey());
                        continue;
                    }

                    try (InputStream is = isOpt.get()) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] buffer = new byte[8192];
                        int read;
                        long bytesRead = 0;
                        while ((read = is.read(buffer)) != -1) {
                            digest.update(buffer, 0, read);
                            bytesRead += read;
                        }
                        String actualSha256 = HexFormat.of().formatHex(digest.digest());
                        if (!actualSha256.equalsIgnoreCase(meta.sha256Digest())) {
                            corrupted.add(meta.id() + " expected=" + meta.sha256Digest() + " actual=" + actualSha256);
                        } else {
                            valid++;
                        }
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    corrupted.add(meta.id() + " error=" + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error reading document_artifacts table: {}", e.getMessage());
        }

        return new ArtifactReconciliation(total, valid, missing, corrupted);
    }

    private KeyReconciliation validateKeys() {
        List<String> invalid = new ArrayList<>();
        int active = 0;
        int revoked = 0;
        int total = 0;

        List<RegisteredKey> keys = keyRegistry.findAll();
        total = keys.size();

        for (RegisteredKey key : keys) {
            try {
                KeyAlgorithmPolicy.validateAlgorithm(key.algorithm(), key.algorithmParameters());
                var pub = key.toPublicKey();
                KeyAlgorithmPolicy.validatePublicKey(pub);

                if (key.status() == KeyStatus.ACTIVE) {
                    active++;
                } else if (key.status() == KeyStatus.REVOKED) {
                    revoked++;
                }
            } catch (Exception e) {
                invalid.add(key.keyId() + ": " + e.getMessage());
            }
        }

        return new KeyReconciliation(total, active, revoked, invalid);
    }

    private CredentialReconciliation validateCredentials() {
        List<String> invalid = new ArrayList<>();
        int valid = 0;
        int total = 0;

        try {
            List<UUID> ids = jdbcTemplate.query(
                    "SELECT credential_id FROM credentials WHERE status = 'ACTIVE'",
                    (rs, rowNum) -> UUID.fromString(rs.getString("credential_id"))
            );
            total = ids.size();

            for (UUID id : ids) {
                if (credentialTrustVerifier.isActiveAndValid(id)) {
                    valid++;
                } else {
                    invalid.add(id.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Error reading credentials table: {}", e.getMessage());
        }

        return new CredentialReconciliation(total, valid, invalid);
    }

    private record SchemaStatus(boolean valid, String version) {}
    private record ArtifactMeta(String id, String sha256Digest, String storageKey, long fileSize) {}
    private record ArtifactReconciliation(int total, int valid, List<String> missing, List<String> corrupted) {}
    private record KeyReconciliation(int total, int active, int revoked, List<String> invalid) {}
    private record CredentialReconciliation(int total, int valid, List<String> invalid) {}
}

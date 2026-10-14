package com.zkdoc.api.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditTamperEvidenceTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcSecurityAuditService auditService;
    private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        java.nio.file.Path tempDb = java.nio.file.Files.createTempFile("zkdoc-audit-tamper-", ".db");
        tempDb.toFile().deleteOnExit();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDb.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("""
                CREATE TABLE security_audit_events (
                    id TEXT PRIMARY KEY,
                    sequence_number INTEGER NOT NULL UNIQUE,
                    event_type TEXT NOT NULL,
                    event_category TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    actor_subject TEXT NOT NULL,
                    actor_type TEXT NOT NULL,
                    target_type TEXT,
                    target_id TEXT,
                    action TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    reason_code TEXT,
                    correlation_id TEXT NOT NULL,
                    client_ip_hash TEXT NOT NULL,
                    metadata_json TEXT,
                    previous_event_hash TEXT NOT NULL,
                    event_hash TEXT NOT NULL
                )
                """);

        clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneId.of("UTC"));
        auditService = new JdbcSecurityAuditService(jdbcTemplate, clock);
    }

    @Test
    @DisplayName("Empty audit trail verifies integrity successfully")
    void emptyAuditTrailVerifies() {
        AuditIntegrityResult result = auditService.verifyChainIntegrity();
        assertThat(result.valid()).isTrue();
        assertThat(result.checkedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Sequential events form valid cryptographic hash chain")
    void sequentialEventsFormValidChain() {
        auditService.record(AuditEventRequest.builder(AuditEventType.AUTHENTICATION_SUCCESS)
                .actor("alice", ActorType.USER)
                .action("LOGIN")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                .actor("issuer-1", ActorType.ISSUER)
                .target(TargetType.CREDENTIAL, UUID.randomUUID())
                .action("ISSUE")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.QR_VERIFICATION_SUCCESS)
                .actor("anonymous", ActorType.ANONYMOUS_PUBLIC)
                .target(TargetType.QR_CAPABILITY, "cap-123")
                .action("PUBLIC_VERIFY")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        AuditIntegrityResult result = auditService.verifyChainIntegrity();
        assertThat(result.valid()).isTrue();
        assertThat(result.checkedCount()).isEqualTo(3);
        assertThat(result.tamperedSequence()).isNull();
    }

    @Test
    @DisplayName("Tampered event action is immediately detected by chain verification")
    void tamperedEventActionIsDetected() {
        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                .actor("issuer-1", ActorType.ISSUER)
                .action("ISSUE")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_SUSPENDED)
                .actor("issuer-1", ActorType.ISSUER)
                .action("SUSPEND")
                .outcome(AuditOutcome.SUCCESS)
                .build());

        // Maliciously tamper with sequence 2 action directly in database
        jdbcTemplate.update("UPDATE security_audit_events SET action = 'MALICIOUS_EDIT' WHERE sequence_number = 2");

        AuditIntegrityResult result = auditService.verifyChainIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.tamperedSequence()).isEqualTo(2);
        assertThat(result.failureReason()).contains("Integrity hash mismatch");
    }

    @Test
    @DisplayName("Deleted event causes sequence break and is immediately flagged")
    void deletedEventIsDetected() {
        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                .actor("issuer-1", ActorType.ISSUER)
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_SUSPENDED)
                .actor("issuer-1", ActorType.ISSUER)
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_REVOKED)
                .actor("issuer-1", ActorType.ISSUER)
                .build());

        // Maliciously delete sequence 2
        jdbcTemplate.update("DELETE FROM security_audit_events WHERE sequence_number = 2");

        AuditIntegrityResult result = auditService.verifyChainIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.tamperedSequence()).isEqualTo(3);
        assertThat(result.failureReason()).contains("Sequence break detected");
    }

    @Test
    @DisplayName("Tampered previous hash is immediately flagged")
    void tamperedPreviousHashIsDetected() {
        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                .actor("issuer-1", ActorType.ISSUER)
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_SUSPENDED)
                .actor("issuer-1", ActorType.ISSUER)
                .build());

        // Corrupt previous_event_hash in sequence 2
        jdbcTemplate.update("UPDATE security_audit_events SET previous_event_hash = 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff' WHERE sequence_number = 2");

        AuditIntegrityResult result = auditService.verifyChainIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.tamperedSequence()).isEqualTo(2);
    }
}

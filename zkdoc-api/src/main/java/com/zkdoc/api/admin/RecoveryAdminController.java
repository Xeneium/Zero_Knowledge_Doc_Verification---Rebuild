package com.zkdoc.api.admin;

import com.zkdoc.api.audit.AuditEventRequest;
import com.zkdoc.api.audit.AuditEventType;
import com.zkdoc.api.audit.AuditOutcome;
import com.zkdoc.api.audit.SecurityAuditService;
import com.zkdoc.api.recovery.BackupRecoveryValidator;
import com.zkdoc.api.recovery.RecoveryReconciliationReport;
import java.security.Principal;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative API endpoint for executing post-restoration reconciliation and integrity verification.
 * Strictly restricted to authenticated users holding the ROLE_ADMIN privilege.
 */
@RestController
@RequestMapping("/api/admin/recovery")
@PreAuthorize("hasRole('ADMIN')")
public class RecoveryAdminController {

    private static final Logger log = LoggerFactory.getLogger(RecoveryAdminController.class);

    private final BackupRecoveryValidator recoveryValidator;
    private final SecurityAuditService auditService;

    public RecoveryAdminController(
            BackupRecoveryValidator recoveryValidator,
            SecurityAuditService auditService) {
        this.recoveryValidator = Objects.requireNonNull(recoveryValidator, "recoveryValidator must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    /**
     * Executes comprehensive post-recovery reconciliation across schema, document artifacts,
     * key registry, verifiable credentials, and security audit hash chains.
     */
    @PostMapping("/reconcile")
    public ResponseEntity<RecoveryReconciliationReport> reconcile(Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        log.info("Recovery reconciliation requested by operator: {}", actor);

        RecoveryReconciliationReport report = recoveryValidator.reconcileAll();

        auditService.record(AuditEventRequest.builder(AuditEventType.ADMIN_SECURITY_OPERATION)
                .actor(actor, com.zkdoc.api.audit.ActorType.USER)
                .target(com.zkdoc.api.audit.TargetType.SYSTEM, "recovery-validator")
                .action("RECONCILE_RECOVERY")
                .outcome(report.isFullyRecovered() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                .reasonCode(report.status().name())
                .metadataJson(String.format("{\"status\":\"%s\",\"artifacts\":%d,\"keys\":%d,\"creds\":%d}",
                        report.status(), report.totalArtifactsChecked(), report.totalKeysChecked(), report.totalCredentialsChecked()))
                .build());

        return ResponseEntity.ok(report);
    }

    /**
     * Returns rapid status check of platform recovery state.
     */
    @GetMapping("/status")
    public ResponseEntity<RecoveryReconciliationReport> getStatus() {
        return ResponseEntity.ok(recoveryValidator.reconcileAll());
    }
}

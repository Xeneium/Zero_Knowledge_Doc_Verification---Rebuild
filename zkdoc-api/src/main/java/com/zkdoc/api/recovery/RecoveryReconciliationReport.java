package com.zkdoc.api.recovery;

import java.time.Instant;
import java.util.List;

/**
 * Detailed report containing reconciliation results across database schema, document artifacts,
 * public key registry, verifiable credentials, and security audit log hash chains.
 */
public record RecoveryReconciliationReport(
        Instant timestamp,
        RecoveryStatus status,
        boolean schemaValid,
        String schemaVersion,
        int totalArtifactsChecked,
        int validArtifacts,
        List<String> missingArtifacts,
        List<String> corruptedArtifacts,
        int totalKeysChecked,
        int activeKeys,
        int revokedKeys,
        List<String> invalidKeys,
        int totalCredentialsChecked,
        int validCredentials,
        List<String> invalidCredentials,
        boolean auditChainValid,
        long totalAuditEvents,
        String auditFailureReason,
        String summary
) {
    public boolean isFullyRecovered() {
        return status == RecoveryStatus.HEALTHY
                && schemaValid
                && missingArtifacts.isEmpty()
                && corruptedArtifacts.isEmpty()
                && invalidKeys.isEmpty()
                && invalidCredentials.isEmpty()
                && auditChainValid;
    }
}

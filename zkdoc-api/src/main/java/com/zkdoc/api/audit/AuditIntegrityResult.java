package com.zkdoc.api.audit;

/**
 * Result of cryptographic verification of the audit trail hash chain.
 */
public record AuditIntegrityResult(
        boolean valid,
        long checkedCount,
        Long tamperedSequence,
        String failureReason
) {
    public static AuditIntegrityResult success(long count) {
        return new AuditIntegrityResult(true, count, null, null);
    }

    public static AuditIntegrityResult failure(long count, long tamperedSequence, String reason) {
        return new AuditIntegrityResult(false, count, tamperedSequence, reason);
    }
}

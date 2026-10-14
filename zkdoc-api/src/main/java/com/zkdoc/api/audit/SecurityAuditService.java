package com.zkdoc.api.audit;

import java.util.List;

/**
 * Service contract for recording and verifying tamper-evident security audit events.
 */
public interface SecurityAuditService {

    /**
     * Records a security audit event synchronously with tamper-evident hash chaining.
     */
    SecurityAuditEvent record(AuditEventRequest request);

    /**
     * Queries the security audit trail using bounded filtering and pagination.
     */
    List<SecurityAuditEvent> findEvents(AuditQueryFilter filter);

    /**
     * Counts the total number of events matching the filter.
     */
    long countEvents(AuditQueryFilter filter);

    /**
     * Verifies the cryptographic integrity of the entire audit hash chain.
     */
    AuditIntegrityResult verifyChainIntegrity();

    /**
     * Verifies the cryptographic integrity of a bounded sequence range.
     */
    AuditIntegrityResult verifyChainIntegrity(long startSeq, long endSeq);
}

package com.zkdoc.api.audit;

import java.time.Instant;

/**
 * Bounded query filter for searching the security audit trail.
 * Strictly limits search parameters to prevent expensive unbounded scans.
 */
public record AuditQueryFilter(
        AuditEventCategory category,
        AuditEventType eventType,
        String actorSubject,
        TargetType targetType,
        String targetId,
        String correlationId,
        Instant from,
        Instant to,
        int page,
        int size
) {
    public AuditQueryFilter {
        // Enforce pagination bounds: min 1, max 100
        if (size <= 0) {
            size = 20;
        } else if (size > 100) {
            size = 100;
        }
        if (page < 0) {
            page = 0;
        }
    }

    public static AuditQueryFilter defaultFilter() {
        return new AuditQueryFilter(null, null, null, null, null, null, null, null, 0, 20);
    }
}

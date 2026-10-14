package com.zkdoc.api.audit;

import java.util.List;

/**
 * Paginated response wrapper for audit trail queries.
 */
public record AuditQueryResult(
        List<SecurityAuditEventResponse> items,
        long totalElements,
        int page,
        int size,
        int totalPages
) {
    public static AuditQueryResult of(List<SecurityAuditEventResponse> items, long totalElements, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new AuditQueryResult(items, totalElements, page, size, totalPages);
    }
}

package com.zkdoc.api.audit;

import java.util.UUID;

/**
 * Dedicated administrative response DTO representing an audit entry.
 * Decouples internal database entities from external API contracts.
 */
public record SecurityAuditEventResponse(
        UUID eventId,
        long sequenceNumber,
        String eventType,
        String eventCategory,
        String timestamp,
        String actorSubject,
        String actorType,
        String targetType,
        String targetId,
        String action,
        String outcome,
        String reasonCode,
        String correlationId,
        String clientIpHash,
        String metadataJson,
        String previousEventHash,
        String eventHash
) {
    public static SecurityAuditEventResponse from(SecurityAuditEvent event) {
        return new SecurityAuditEventResponse(
                event.eventId(),
                event.sequenceNumber(),
                event.eventType().name(),
                event.eventCategory().name(),
                event.timestamp().toString(),
                event.actorSubject(),
                event.actorType().name(),
                event.targetType() != null ? event.targetType().name() : null,
                event.targetId(),
                event.action(),
                event.outcome().name(),
                event.reasonCode(),
                event.correlationId(),
                event.clientIpHash(),
                event.metadataJson(),
                event.previousEventHash(),
                event.eventHash()
        );
    }
}

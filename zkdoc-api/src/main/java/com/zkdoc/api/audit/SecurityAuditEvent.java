package com.zkdoc.api.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical immutable security audit event representing durable, tamper-evident security evidence.
 * Contains only authorized metadata and non-secret identifiers; never contains private keys,
 * passwords, JWTs, raw attributes, blinding factors, witnesses, or document contents.
 */
public record SecurityAuditEvent(
        UUID eventId,
        long sequenceNumber,
        AuditEventType eventType,
        AuditEventCategory eventCategory,
        Instant timestamp,
        String actorSubject,
        ActorType actorType,
        TargetType targetType,
        String targetId,
        String action,
        AuditOutcome outcome,
        String reasonCode,
        String correlationId,
        String clientIpHash,
        String metadataJson,
        String previousEventHash,
        String eventHash
) {
    public SecurityAuditEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(eventCategory, "eventCategory must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(actorSubject, "actorSubject must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(clientIpHash, "clientIpHash must not be null");
        Objects.requireNonNull(previousEventHash, "previousEventHash must not be null");
        Objects.requireNonNull(eventHash, "eventHash must not be null");
    }
}

package com.zkdoc.credential;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain event recording a credential lifecycle state transition.
 */
public record CredentialLifecycleEvent(
        UUID id,
        UUID credentialId,
        String eventType,
        String actor,
        String actorRole,
        CredentialStatus fromStatus,
        CredentialStatus toStatus,
        String reason,
        Instant timestamp,
        String metadataJson) {

    public CredentialLifecycleEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static CredentialLifecycleEvent create(
            UUID credentialId,
            String eventType,
            String actor,
            String actorRole,
            CredentialStatus fromStatus,
            CredentialStatus toStatus,
            String reason,
            Instant timestamp) {
        return new CredentialLifecycleEvent(
                UUID.randomUUID(),
                credentialId,
                eventType,
                actor,
                actorRole,
                fromStatus,
                toStatus,
                reason,
                timestamp,
                null);
    }
}

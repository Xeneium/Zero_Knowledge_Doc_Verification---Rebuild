package com.zkdoc.keys;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit record capturing security-critical key lifecycle events.
 * Never captures private keys, secrets, seeds, or confidential payloads.
 */
public record KeyLifecycleAuditEvent(
        UUID id,
        String eventType,
        String keyId,
        KeyPurpose purpose,
        String ownerId,
        String actor,
        String result,
        String detail,
        Instant timestamp) {

    public KeyLifecycleAuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(result, "result must not be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static KeyLifecycleAuditEvent of(
            String eventType,
            String keyId,
            KeyPurpose purpose,
            String ownerId,
            String actor,
            String result,
            String detail) {
        return new KeyLifecycleAuditEvent(
                UUID.randomUUID(),
                eventType,
                keyId,
                purpose,
                ownerId,
                actor != null ? actor : "system",
                result,
                detail,
                Instant.now());
    }
}

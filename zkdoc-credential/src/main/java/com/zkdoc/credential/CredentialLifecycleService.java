package com.zkdoc.credential;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production domain service enforcing the explicit lifecycle state machine for credentials.
 * Decouples administrative/operational lifecycle from digital signature validity.
 * Ensures:
 * 1. Strict state machine transitions (e.g. REVOKED is terminal).
 * 2. Optimistic concurrency control (via version column) across distributed instances.
 * 3. Immutable audit trail of every state transition.
 * 4. Deterministic time evaluation via injectable Clock.
 * 5. Idempotent operations for duplicate requests.
 */
@Service
public class CredentialLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CredentialLifecycleService.class);

    private final CredentialStore credentialStore;
    private final CredentialLifecycleEventStore eventStore;
    private final Clock clock;

    @Autowired
    public CredentialLifecycleService(
            CredentialStore credentialStore,
            CredentialLifecycleEventStore eventStore,
            @Autowired(required = false) Clock clock) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public CredentialLifecycleService(
            CredentialStore credentialStore,
            CredentialLifecycleEventStore eventStore) {
        this(credentialStore, eventStore, Clock.systemUTC());
    }

    @Transactional
    public StoredCredential suspend(UUID credentialId, String actorId, String actorRole, String reason) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        StoredCredential stored = credentialStore.findById(credentialId)
                .orElseThrow(() -> new CredentialNotFoundException(credentialId));

        // Idempotency: repeated suspension returns current state deterministically
        if (stored.status() == CredentialStatus.SUSPENDED) {
            log.info("Credential {} is already suspended; returning current state idempotently", credentialId);
            return stored;
        }

        // Terminal state defense
        if (stored.status() == CredentialStatus.REVOKED) {
            throw new InvalidLifecycleTransitionException(credentialId, stored.status(), CredentialStatus.SUSPENDED);
        }

        Instant now = clock.instant();
        if (!now.isBefore(stored.expiresAt())) {
            throw new InvalidLifecycleTransitionException(credentialId, CredentialStatus.EXPIRED, CredentialStatus.SUSPENDED);
        }

        if (!stored.status().canTransitionTo(CredentialStatus.SUSPENDED)) {
            throw new InvalidLifecycleTransitionException(credentialId, stored.status(), CredentialStatus.SUSPENDED);
        }

        boolean updated = credentialStore.updateLifecycle(
                credentialId,
                CredentialStatus.SUSPENDED,
                stored.version(),
                stored.status(),
                now,
                reason,
                stored.revokedAt(),
                stored.revocationReason());

        if (!updated) {
            throw new ConcurrentLifecycleModificationException(credentialId);
        }

        eventStore.record(CredentialLifecycleEvent.create(
                credentialId,
                "SUSPEND",
                actorId != null ? actorId : "system",
                actorRole != null ? actorRole : "ISSUER",
                stored.status(),
                CredentialStatus.SUSPENDED,
                reason,
                now));

        log.info("Credential {} suspended by actor={}", credentialId, actorId);
        return new StoredCredential(
                stored.credentialId(),
                stored.issuerId(),
                stored.issuedAt(),
                stored.expiresAt(),
                CredentialStatus.SUSPENDED,
                stored.canonicalPayload(),
                stored.digest(),
                stored.signature(),
                now,
                reason,
                stored.revokedAt(),
                stored.revocationReason(),
                stored.version() + 1);
    }

    @Transactional
    public StoredCredential resume(UUID credentialId, String actorId, String actorRole, String reason) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        StoredCredential stored = credentialStore.findById(credentialId)
                .orElseThrow(() -> new CredentialNotFoundException(credentialId));

        // Idempotency: repeated resume on active credential returns current state deterministically
        if (stored.status() == CredentialStatus.ACTIVE) {
            log.info("Credential {} is already active; returning current state idempotently", credentialId);
            return stored;
        }

        // Terminal state defense
        if (stored.status() == CredentialStatus.REVOKED) {
            throw new InvalidLifecycleTransitionException(credentialId, stored.status(), CredentialStatus.ACTIVE);
        }

        Instant now = clock.instant();
        if (!now.isBefore(stored.expiresAt())) {
            throw new InvalidLifecycleTransitionException(credentialId, CredentialStatus.EXPIRED, CredentialStatus.ACTIVE);
        }

        if (!stored.status().canTransitionTo(CredentialStatus.ACTIVE)) {
            throw new InvalidLifecycleTransitionException(credentialId, stored.status(), CredentialStatus.ACTIVE);
        }

        boolean updated = credentialStore.updateLifecycle(
                credentialId,
                CredentialStatus.ACTIVE,
                stored.version(),
                stored.status(),
                null,
                null,
                stored.revokedAt(),
                stored.revocationReason());

        if (!updated) {
            throw new ConcurrentLifecycleModificationException(credentialId);
        }

        eventStore.record(CredentialLifecycleEvent.create(
                credentialId,
                "RESUME",
                actorId != null ? actorId : "system",
                actorRole != null ? actorRole : "ISSUER",
                stored.status(),
                CredentialStatus.ACTIVE,
                reason,
                now));

        log.info("Credential {} resumed to ACTIVE by actor={}", credentialId, actorId);
        return new StoredCredential(
                stored.credentialId(),
                stored.issuerId(),
                stored.issuedAt(),
                stored.expiresAt(),
                CredentialStatus.ACTIVE,
                stored.canonicalPayload(),
                stored.digest(),
                stored.signature(),
                null,
                null,
                stored.revokedAt(),
                stored.revocationReason(),
                stored.version() + 1);
    }

    @Transactional
    public StoredCredential revoke(UUID credentialId, String actorId, String actorRole, String reason) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        StoredCredential stored = credentialStore.findById(credentialId)
                .orElseThrow(() -> new CredentialNotFoundException(credentialId));

        // Idempotency: repeated revocation returns current state deterministically without creating duplicate events
        if (stored.status() == CredentialStatus.REVOKED) {
            log.info("Credential {} is already revoked; returning current state idempotently", credentialId);
            return stored;
        }

        if (!stored.status().canTransitionTo(CredentialStatus.REVOKED)) {
            throw new InvalidLifecycleTransitionException(credentialId, stored.status(), CredentialStatus.REVOKED);
        }

        Instant now = clock.instant();
        boolean updated = credentialStore.updateLifecycle(
                credentialId,
                CredentialStatus.REVOKED,
                stored.version(),
                stored.status(),
                stored.suspendedAt(),
                stored.suspensionReason(),
                now,
                reason);

        if (!updated) {
            throw new ConcurrentLifecycleModificationException(credentialId);
        }

        eventStore.record(CredentialLifecycleEvent.create(
                credentialId,
                "REVOKE",
                actorId != null ? actorId : "system",
                actorRole != null ? actorRole : "ISSUER",
                stored.status(),
                CredentialStatus.REVOKED,
                reason,
                now));

        log.info("Credential {} revoked by actor={}", credentialId, actorId);
        return new StoredCredential(
                stored.credentialId(),
                stored.issuerId(),
                stored.issuedAt(),
                stored.expiresAt(),
                CredentialStatus.REVOKED,
                stored.canonicalPayload(),
                stored.digest(),
                stored.signature(),
                stored.suspendedAt(),
                stored.suspensionReason(),
                now,
                reason,
                stored.version() + 1);
    }

    /**
     * Computes the effective operational status considering authoritative expiration timestamp.
     */
    public CredentialStatus evaluateEffectiveStatus(StoredCredential credential) {
        return evaluateEffectiveStatus(credential, clock.instant());
    }

    /**
     * Computes the effective operational status at a specific point in time.
     */
    public CredentialStatus evaluateEffectiveStatus(StoredCredential credential, Instant now) {
        if (credential == null) {
            return null;
        }
        if (credential.status() == CredentialStatus.REVOKED) {
            return CredentialStatus.REVOKED;
        }
        if (credential.status() == CredentialStatus.SUSPENDED) {
            return CredentialStatus.SUSPENDED;
        }
        if (!now.isBefore(credential.expiresAt())) {
            return CredentialStatus.EXPIRED;
        }
        return credential.status();
    }

    public List<CredentialLifecycleEvent> getHistory(UUID credentialId) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        return eventStore.findByCredentialId(credentialId);
    }
}

package com.zkdoc.credential;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CredentialLifecycleServiceTest {

    private InMemoryCredentialStore credentialStore;
    private InMemoryLifecycleEventStore eventStore;
    private MutableClock clock;
    private CredentialLifecycleService service;

    private UUID credentialId;
    private StoredCredential initialCredential;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        eventStore = new InMemoryLifecycleEventStore();
        clock = new MutableClock(Instant.parse("2026-06-01T12:00:00Z"));
        service = new CredentialLifecycleService(credentialStore, eventStore, clock);

        credentialId = UUID.randomUUID();
        initialCredential = new StoredCredential(
                credentialId,
                "issuer-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"),
                CredentialStatus.ACTIVE,
                new byte[]{1, 2, 3},
                new byte[]{4, 5, 6},
                new byte[]{7, 8, 9},
                null,
                null,
                null,
                null,
                1);
        credentialStore.saveRaw(initialCredential);
    }

    @Test
    void testSuspendActiveCredential() {
        StoredCredential suspended = service.suspend(credentialId, "issuer-1", "ISSUER", "Investigation pending");

        assertEquals(CredentialStatus.SUSPENDED, suspended.status());
        assertEquals("Investigation pending", suspended.suspensionReason());
        assertNotNull(suspended.suspendedAt());
        assertEquals(2, suspended.version());

        List<CredentialLifecycleEvent> history = service.getHistory(credentialId);
        assertEquals(1, history.size());
        assertEquals("SUSPEND", history.get(0).eventType());
        assertEquals(CredentialStatus.ACTIVE, history.get(0).fromStatus());
        assertEquals(CredentialStatus.SUSPENDED, history.get(0).toStatus());
    }

    @Test
    void testSuspendIdempotency() {
        service.suspend(credentialId, "issuer-1", "ISSUER", "First");
        StoredCredential second = service.suspend(credentialId, "issuer-1", "ISSUER", "Second");

        assertEquals(CredentialStatus.SUSPENDED, second.status());
        assertEquals(1, service.getHistory(credentialId).size(), "Idempotent call should not create duplicate events");
    }

    @Test
    void testResumeSuspendedCredential() {
        service.suspend(credentialId, "issuer-1", "ISSUER", "Hold");
        StoredCredential resumed = service.resume(credentialId, "issuer-1", "ISSUER", "Cleared");

        assertEquals(CredentialStatus.ACTIVE, resumed.status());
        assertNull(resumed.suspendedAt());
        assertNull(resumed.suspensionReason());
        assertEquals(3, resumed.version());

        List<CredentialLifecycleEvent> history = service.getHistory(credentialId);
        assertEquals(2, history.size());
        assertEquals("RESUME", history.get(1).eventType());
    }

    @Test
    void testResumeIdempotency() {
        StoredCredential active = service.resume(credentialId, "issuer-1", "ISSUER", "No-op");
        assertEquals(CredentialStatus.ACTIVE, active.status());
        assertEquals(0, service.getHistory(credentialId).size());
    }

    @Test
    void testRevokeActiveCredential() {
        StoredCredential revoked = service.revoke(credentialId, "issuer-1", "ISSUER", "Key compromised");

        assertEquals(CredentialStatus.REVOKED, revoked.status());
        assertEquals("Key compromised", revoked.revocationReason());
        assertNotNull(revoked.revokedAt());
        assertEquals(2, revoked.version());

        List<CredentialLifecycleEvent> history = service.getHistory(credentialId);
        assertEquals(1, history.size());
        assertEquals("REVOKE", history.get(0).eventType());
    }

    @Test
    void testRevokeIdempotency() {
        service.revoke(credentialId, "issuer-1", "ISSUER", "First");
        StoredCredential second = service.revoke(credentialId, "issuer-1", "ISSUER", "Second");

        assertEquals(CredentialStatus.REVOKED, second.status());
        assertEquals(1, service.getHistory(credentialId).size());
    }

    @Test
    void testResumeRevokedCredentialThrows() {
        service.revoke(credentialId, "issuer-1", "ISSUER", "Compromised");
        assertThrows(InvalidLifecycleTransitionException.class, () ->
                service.resume(credentialId, "issuer-1", "ISSUER", "Re-activate"));
    }

    @Test
    void testSuspendRevokedCredentialThrows() {
        service.revoke(credentialId, "issuer-1", "ISSUER", "Compromised");
        assertThrows(InvalidLifecycleTransitionException.class, () ->
                service.suspend(credentialId, "issuer-1", "ISSUER", "Hold"));
    }

    @Test
    void testOptimisticLockConflictThrows() {
        credentialStore.setSimulateConflict(true);
        assertThrows(ConcurrentLifecycleModificationException.class, () ->
                service.suspend(credentialId, "issuer-1", "ISSUER", "Conflict test"));
    }

    @Test
    void testEvaluateEffectiveStatusExpiration() {
        StoredCredential cred = credentialStore.findById(credentialId).orElseThrow();
        assertEquals(CredentialStatus.ACTIVE, service.evaluateEffectiveStatus(cred));

        // Advance clock past expiration
        clock.setInstant(Instant.parse("2027-01-01T00:00:00Z"));
        assertEquals(CredentialStatus.EXPIRED, service.evaluateEffectiveStatus(cred));

        // Exactly at expiration time: exclusive upper bound => EXPIRED
        clock.setInstant(cred.expiresAt());
        assertEquals(CredentialStatus.EXPIRED, service.evaluateEffectiveStatus(cred));

        // 1 second before expiration: ACTIVE
        clock.setInstant(cred.expiresAt().minusSeconds(1));
        assertEquals(CredentialStatus.ACTIVE, service.evaluateEffectiveStatus(cred));
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;

        MutableClock(Instant initial) {
            this.currentInstant = initial;
        }

        void setInstant(Instant instant) {
            this.currentInstant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    private static class InMemoryCredentialStore implements CredentialStore {
        private final List<StoredCredential> list = new ArrayList<>();
        private boolean simulateConflict = false;

        void setSimulateConflict(boolean simulateConflict) {
            this.simulateConflict = simulateConflict;
        }

        void saveRaw(StoredCredential sc) {
            list.removeIf(c -> c.credentialId().equals(sc.credentialId()));
            list.add(sc);
        }

        @Override
        public void save(SignedCredential credential) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredCredential> findById(UUID id) {
            return list.stream().filter(c -> c.credentialId().equals(id)).findFirst();
        }

        @Override
        public boolean updateLifecycle(
                UUID id,
                CredentialStatus targetStatus,
                int expectedVersion,
                CredentialStatus previousStatus,
                Instant suspendedAt,
                String suspensionReason,
                Instant revokedAt,
                String revocationReason) {
            if (simulateConflict) {
                return false;
            }
            Optional<StoredCredential> opt = findById(id);
            if (opt.isEmpty() || opt.get().version() != expectedVersion) {
                return false;
            }
            StoredCredential old = opt.get();
            StoredCredential updated = new StoredCredential(
                    old.credentialId(),
                    old.issuerId(),
                    old.issuedAt(),
                    old.expiresAt(),
                    targetStatus,
                    old.canonicalPayload(),
                    old.digest(),
                    old.signature(),
                    suspendedAt,
                    suspensionReason,
                    revokedAt,
                    revocationReason,
                    old.version() + 1);
            saveRaw(updated);
            return true;
        }
    }

    private static class InMemoryLifecycleEventStore implements CredentialLifecycleEventStore {
        private final List<CredentialLifecycleEvent> events = new ArrayList<>();

        @Override
        public void record(CredentialLifecycleEvent event) {
            events.add(event);
        }

        @Override
        public List<CredentialLifecycleEvent> findByCredentialId(UUID credId) {
            return events.stream().filter(e -> e.credentialId().equals(credId)).toList();
        }
    }
}

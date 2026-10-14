package com.zkdoc.credential;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CredentialLifecycleConcurrencyTest {

    @Test
    void testConcurrentDuplicateRevocations() throws Exception {
        UUID credentialId = UUID.randomUUID();
        ConcurrentCredentialStore store = new ConcurrentCredentialStore();
        ConcurrentLifecycleEventStore eventStore = new ConcurrentLifecycleEventStore();

        StoredCredential initial = new StoredCredential(
                credentialId,
                "issuer-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"),
                CredentialStatus.ACTIVE,
                new byte[]{1},
                new byte[]{2},
                new byte[]{3},
                null,
                null,
                null,
                null,
                1);
        store.saveRaw(initial);

        CredentialLifecycleService service = new CredentialLifecycleService(store, eventStore, Clock.systemUTC());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.revoke(credentialId, "issuer-1", "ISSUER", "Reason " + idx);
                    successCount.incrementAndGet();
                } catch (ConcurrentLifecycleModificationException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        StoredCredential finalCred = store.findById(credentialId).orElseThrow();
        assertEquals(CredentialStatus.REVOKED, finalCred.status());
        // At least one thread succeeded in revoking
        assertTrue(successCount.get() >= 1, "At least one revocation must succeed");
    }

    @Test
    void testConcurrentSuspendAndRevoke() throws Exception {
        UUID credentialId = UUID.randomUUID();
        ConcurrentCredentialStore store = new ConcurrentCredentialStore();
        ConcurrentLifecycleEventStore eventStore = new ConcurrentLifecycleEventStore();

        StoredCredential initial = new StoredCredential(
                credentialId,
                "issuer-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"),
                CredentialStatus.ACTIVE,
                new byte[]{1},
                new byte[]{2},
                new byte[]{3},
                null,
                null,
                null,
                null,
                1);
        store.saveRaw(initial);

        CredentialLifecycleService service = new CredentialLifecycleService(store, eventStore, Clock.systemUTC());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Exception> suspendEx = new AtomicReference<>();
        AtomicReference<Exception> revokeEx = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                service.suspend(credentialId, "issuer-1", "ISSUER", "Concurrent suspend");
            } catch (Exception e) {
                suspendEx.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                service.revoke(credentialId, "issuer-1", "ISSUER", "Concurrent revoke");
            } catch (Exception e) {
                revokeEx.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        StoredCredential finalCred = store.findById(credentialId).orElseThrow();
        // The final state must be either SUSPENDED or REVOKED, never corrupted or in illegal state
        assertTrue(finalCred.status() == CredentialStatus.SUSPENDED || finalCred.status() == CredentialStatus.REVOKED);
    }

    private static class ConcurrentCredentialStore implements CredentialStore {
        private final Map<UUID, StoredCredential> map = new ConcurrentHashMap<>();

        void saveRaw(StoredCredential sc) {
            map.put(sc.credentialId(), sc);
        }

        @Override
        public void save(SignedCredential credential) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredCredential> findById(UUID id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public synchronized boolean updateLifecycle(
                UUID id,
                CredentialStatus targetStatus,
                int expectedVersion,
                CredentialStatus previousStatus,
                Instant suspendedAt,
                String suspensionReason,
                Instant revokedAt,
                String revocationReason) {
            StoredCredential existing = map.get(id);
            if (existing == null || existing.version() != expectedVersion) {
                return false;
            }
            StoredCredential updated = new StoredCredential(
                    existing.credentialId(),
                    existing.issuerId(),
                    existing.issuedAt(),
                    existing.expiresAt(),
                    targetStatus,
                    existing.canonicalPayload(),
                    existing.digest(),
                    existing.signature(),
                    suspendedAt,
                    suspensionReason,
                    revokedAt,
                    revocationReason,
                    existing.version() + 1);
            map.put(id, updated);
            return true;
        }
    }

    private static class ConcurrentLifecycleEventStore implements CredentialLifecycleEventStore {
        private final List<CredentialLifecycleEvent> events = new CopyOnWriteArrayList<>();

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

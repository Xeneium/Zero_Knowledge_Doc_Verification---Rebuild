package com.zkdoc.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolderCredentialServiceTest {

    private InMemoryHolderCredentialStore holderStore;
    private InMemoryCredentialStore credentialStore;
    private HolderCredentialService service;

    private UUID validCredentialId;
    private byte[] validDigest;

    @BeforeEach
    void setUp() throws Exception {
        holderStore = new InMemoryHolderCredentialStore();
        credentialStore = new InMemoryCredentialStore();
        service = new HolderCredentialService(holderStore, credentialStore);

        validCredentialId = UUID.randomUUID();
        validDigest = MessageDigest.getInstance("SHA-256").digest("test-canonical-bytes".getBytes(StandardCharsets.UTF_8));
        StoredCredential stored = new StoredCredential(
                validCredentialId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.ACTIVE,
                "test-canonical-bytes".getBytes(StandardCharsets.UTF_8),
                validDigest,
                new byte[] {1, 2, 3});
        credentialStore.save(stored);
    }

    @Test
    @DisplayName("Provisioning: successfully provisions active holder credential bound to credential digest")
    void provisionSuccess() {
        HolderId holderId = HolderId.of("user-holder-1");
        CredentialId credId = CredentialId.of(validCredentialId);

        HolderCredential hc = service.provision(holderId, credId);

        assertNotNull(hc.id());
        assertEquals(holderId, hc.holderId());
        assertEquals(credId, hc.credentialId());
        assertEquals(HolderCredentialStatus.ACTIVE, hc.status());
        assertEquals(1, hc.version());
        assertTrue(MessageDigest.isEqual(validDigest, hc.credentialDigest()));
    }

    @Test
    @DisplayName("Idempotency: duplicate provisioning returns existing record without throwing error")
    void duplicateProvisioningIdempotent() {
        HolderId holderId = HolderId.of("user-holder-1");
        CredentialId credId = CredentialId.of(validCredentialId);

        HolderCredential first = service.provision(holderId, credId);
        HolderCredential second = service.provision(holderId, credId);

        assertEquals(first.id(), second.id());
        assertEquals(1, holderStore.credentials.size());
    }

    @Test
    @DisplayName("Security: cannot provision non-existent or inactive credential")
    void cannotProvisionInactiveOrNonExistentCredential() {
        HolderId holderId = HolderId.of("user-holder-1");
        CredentialId unknownCredId = CredentialId.random();

        assertThrows(CredentialNotFoundException.class, () ->
                service.provision(holderId, unknownCredId));

        // Create revoked credential
        UUID revokedId = UUID.randomUUID();
        credentialStore.save(new StoredCredential(
                revokedId,
                Instant.now().plusSeconds(3600),
                CredentialStatus.REVOKED,
                "revoked-bytes".getBytes(StandardCharsets.UTF_8),
                new byte[] {1},
                new byte[] {2}));

        assertThrows(IllegalStateException.class, () ->
                service.provision(holderId, CredentialId.of(revokedId)));
    }

    @Test
    @DisplayName("IDOR Protection: Holder A cannot access Holder B's credential")
    void idorProtectionOnAccess() {
        HolderId holderA = HolderId.of("user-holder-a");
        HolderId holderB = HolderId.of("user-holder-b");
        CredentialId credId = CredentialId.of(validCredentialId);

        HolderCredential hcB = service.provision(holderB, credId);

        // Holder B can access own credential
        HolderCredential accessed = service.findByIdAndHolder(hcB.id(), holderB);
        assertEquals(hcB.id(), accessed.id());

        // Holder A attempting to access Holder B's credential fails with not found / denied
        assertThrows(HolderCredentialNotFoundException.class, () ->
                service.findByIdAndHolder(hcB.id(), holderA));
    }

    @Test
    @DisplayName("Lifecycle & Revocation: Holder can revoke own credential, updating status and version")
    void revokeLifecycle() {
        HolderId holder = HolderId.of("user-holder-1");
        CredentialId credId = CredentialId.of(validCredentialId);

        HolderCredential hc = service.provision(holder, credId);
        assertTrue(hc.status().isUsable());

        HolderCredential revoked = service.revoke(hc.id(), holder);
        assertEquals(HolderCredentialStatus.REVOKED, revoked.status());
        assertFalse(revoked.status().isUsable());
        assertEquals(2, revoked.version());

        // Another holder cannot revoke Holder's credential
        HolderId attacker = HolderId.of("attacker");
        assertThrows(HolderCredentialNotFoundException.class, () ->
                service.revoke(hc.id(), attacker));
    }

    @Test
    @DisplayName("Lifecycle & Suspension: Holder can suspend and reactivate credential with state machine enforcement")
    void suspendAndReactivateLifecycle() {
        HolderId holder = HolderId.of("user-holder-1");
        CredentialId credId = CredentialId.of(validCredentialId);

        HolderCredential hc = service.provision(holder, credId);
        assertTrue(hc.status().isUsable());

        // Suspend
        HolderCredential suspended = service.suspend(hc.id(), holder);
        assertEquals(HolderCredentialStatus.SUSPENDED, suspended.status());
        assertFalse(suspended.status().isUsable());
        assertEquals(2, suspended.version());

        // Reactivate
        HolderCredential reactivated = service.reactivate(hc.id(), holder);
        assertEquals(HolderCredentialStatus.ACTIVE, reactivated.status());
        assertTrue(reactivated.status().isUsable());
        assertEquals(3, reactivated.version());

        // Revoke from active
        HolderCredential revoked = service.revoke(hc.id(), holder);
        assertEquals(HolderCredentialStatus.REVOKED, revoked.status());

        // Cannot reactivate or suspend a revoked credential (terminal state)
        assertThrows(IllegalStateException.class, () ->
                service.reactivate(hc.id(), holder));
        assertThrows(IllegalStateException.class, () ->
                service.suspend(hc.id(), holder));
    }

    private static class InMemoryHolderCredentialStore implements HolderCredentialStore {
        private final List<HolderCredential> credentials = new ArrayList<>();

        @Override
        public void save(HolderCredential holderCredential) {
            credentials.add(holderCredential);
        }

        @Override
        public Optional<HolderCredential> findById(HolderCredentialId id) {
            return credentials.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public Optional<HolderCredential> findByHolderIdAndCredentialId(HolderId holderId, CredentialId credentialId) {
            return credentials.stream()
                    .filter(c -> c.holderId().equals(holderId) && c.credentialId().equals(credentialId))
                    .findFirst();
        }

        @Override
        public List<HolderCredential> findAllByHolderId(HolderId holderId) {
            return credentials.stream().filter(c -> c.holderId().equals(holderId)).toList();
        }

        @Override
        public void updateStatus(HolderCredentialId id, HolderCredentialStatus status) {
            for (int i = 0; i < credentials.size(); i++) {
                HolderCredential c = credentials.get(i);
                if (c.id().equals(id)) {
                    credentials.set(i, c.transitionTo(status));
                    return;
                }
            }
        }

        @Override
        public boolean existsByHolderIdAndCredentialId(HolderId holderId, CredentialId credentialId) {
            return credentials.stream().anyMatch(c -> c.holderId().equals(holderId) && c.credentialId().equals(credentialId));
        }
    }

    private static class InMemoryCredentialStore implements CredentialStore {
        private final List<StoredCredential> list = new ArrayList<>();

        public void save(StoredCredential c) {
            list.add(c);
        }

        @Override
        public void save(SignedCredential signedCredential) {
            list.add(new StoredCredential(
                    signedCredential.credential().id().value(),
                    signedCredential.credential().expiresAt(),
                    signedCredential.credential().status(),
                    signedCredential.canonicalPayload(),
                    signedCredential.digest(),
                    signedCredential.signature()));
        }

        @Override
        public Optional<StoredCredential> findById(UUID credentialId) {
            return list.stream().filter(c -> c.credentialId().equals(credentialId)).findFirst();
        }
    }
}

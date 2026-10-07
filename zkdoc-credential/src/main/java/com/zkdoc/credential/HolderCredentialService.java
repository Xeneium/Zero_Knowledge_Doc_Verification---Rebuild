package com.zkdoc.credential;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service managing the lifecycle and provisioning of Holder Credentials.
 * Enforces ownership checks, credential validity checks, and strict boundary isolation.
 */
@Service
public class HolderCredentialService {

    private static final Logger log = LoggerFactory.getLogger(HolderCredentialService.class);

    private final HolderCredentialStore holderCredentialStore;
    private final CredentialStore credentialStore;

    public HolderCredentialService(
            HolderCredentialStore holderCredentialStore,
            CredentialStore credentialStore) {
        this.holderCredentialStore = Objects.requireNonNull(holderCredentialStore, "holderCredentialStore must not be null");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore must not be null");
    }

    /**
     * Provisions a holder credential for an authenticated holder subject against an existing issued credential.
     * Transactional and idempotent: prevents duplicate provisioning under race conditions.
     */
    @Transactional
    public HolderCredential provision(HolderId holderId, CredentialId credentialId) {
        Objects.requireNonNull(holderId, "holderId must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");

        // 1. Verify that authoritative credential exists and is active
        StoredCredential stored = credentialStore.findById(credentialId.value())
                .orElseThrow(() -> new CredentialNotFoundException(credentialId.value()));

        if (stored.status() != CredentialStatus.ACTIVE) {
            throw new IllegalStateException("Cannot provision credential in non-active status: " + stored.status());
        }

        // 2. Check for duplicate provisioning
        Optional<HolderCredential> existing = holderCredentialStore.findByHolderIdAndCredentialId(holderId, credentialId);
        if (existing.isPresent()) {
            log.info("Holder credential already provisioned for holder={} and credential={}", holderId, credentialId);
            return existing.get();
        }

        // 3. Create holder credential binding
        HolderCredential holderCredential = HolderCredential.create(holderId, credentialId, stored.digest());
        holderCredentialStore.save(holderCredential);

        log.info("Holder credential provisioned: holderCredentialId={}, holder={}, credential={}",
                holderCredential.id(), holderId, credentialId);
        return holderCredential;
    }

    /**
     * Finds a holder credential by its ID, enforcing that the requesting caller owns the record.
     */
    public HolderCredential findByIdAndHolder(HolderCredentialId id, HolderId holderId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(holderId, "holderId must not be null");

        HolderCredential hc = holderCredentialStore.findById(id)
                .orElseThrow(() -> new HolderCredentialNotFoundException(id.value()));

        if (!hc.holderId().equals(holderId)) {
            log.warn("IDOR access violation: Holder {} attempted to access credential {} belonging to {}",
                    holderId, id, hc.holderId());
            throw new HolderCredentialNotFoundException("Holder credential not found or access denied");
        }

        return hc;
    }

    /**
     * Lists all holder credentials owned by a given holder.
     */
    public List<HolderCredential> listByHolder(HolderId holderId) {
        Objects.requireNonNull(holderId, "holderId must not be null");
        return holderCredentialStore.findAllByHolderId(holderId);
    }

    /**
     * Revokes a holder credential, enforcing that the requesting caller owns the record.
     */
    @Transactional
    public HolderCredential revoke(HolderCredentialId id, HolderId holderId) {
        HolderCredential hc = findByIdAndHolder(id, holderId);
        HolderCredential updated = hc.transitionTo(HolderCredentialStatus.REVOKED);
        holderCredentialStore.updateStatus(id, HolderCredentialStatus.REVOKED);
        log.info("Holder credential revoked: id={}, holder={}", id, holderId);
        return updated;
    }
}

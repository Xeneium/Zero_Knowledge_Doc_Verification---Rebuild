package com.zkdoc.zkp;

import com.zkdoc.credential.CredentialNotFoundException;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.HolderCredential;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderId;
import com.zkdoc.credential.StoredCredential;
import java.security.MessageDigest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link HolderProvingMaterialAccess}.
 * <p>
 * Enforces the full defense-in-depth proving boundary:
 * <ol>
 *   <li>Ownership & BOLA verification: enforces {@code holderId} matches credential ownership via {@link HolderCredentialService}.</li>
 *   <li>Holder credential lifecycle check: fails closed if status is not {@code ACTIVE} (e.g. SUSPENDED or REVOKED).</li>
 *   <li>Underlying credential lifecycle check: verifies issuer-side status is {@code ACTIVE}.</li>
 *   <li>Integrity verification: validates holder credential digest matches canonical stored credential digest.</li>
 *   <li>Commitment resolution: retrieves authoritative {@link AttributeCommitment} from {@link CommitmentStore}.</li>
 *   <li>Secret retrieval: fetches isolated {@link AttributeOpeningSecret} from {@link HolderProvingMaterialStore}.</li>
 *   <li>Cryptographic continuity check: computes \( C = vG + rH \) and verifies opening matches commitment point.</li>
 *   <li>Constructs and returns {@link HolderProofContext}.</li>
 * </ol>
 */
@Service
public class DefaultHolderProvingMaterialAccess implements HolderProvingMaterialAccess {

    private static final Logger log = LoggerFactory.getLogger(DefaultHolderProvingMaterialAccess.class);
    private static final String PROTOCOL_VERSION = "ZKDOC-BP-1.0";

    private final HolderCredentialService holderCredentialService;
    private final CredentialStore credentialStore;
    private final CommitmentStore commitmentStore;
    private final HolderProvingMaterialStore provingMaterialStore;
    private final PedersenCommitmentService pedersenCommitmentService;

    public DefaultHolderProvingMaterialAccess(
            HolderCredentialService holderCredentialService,
            CredentialStore credentialStore,
            CommitmentStore commitmentStore,
            HolderProvingMaterialStore provingMaterialStore,
            PedersenCommitmentService pedersenCommitmentService) {
        this.holderCredentialService = Objects.requireNonNull(holderCredentialService, "holderCredentialService must not be null");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore must not be null");
        this.commitmentStore = Objects.requireNonNull(commitmentStore, "commitmentStore must not be null");
        this.provingMaterialStore = Objects.requireNonNull(provingMaterialStore, "provingMaterialStore must not be null");
        this.pedersenCommitmentService = Objects.requireNonNull(pedersenCommitmentService, "pedersenCommitmentService must not be null");
    }

    @Override
    public HolderProofContext resolveProofContext(
            HolderId holderId, HolderCredentialId holderCredentialId, String attributeName) {
        Objects.requireNonNull(holderId, "holderId must not be null");
        Objects.requireNonNull(holderCredentialId, "holderCredentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");

        // 1. Enforce IDOR protection and ownership validation
        HolderCredential hc = holderCredentialService.findByIdAndHolder(holderCredentialId, holderId);

        // 2. Validate Holder Credential Lifecycle
        if (!hc.status().isUsable()) {
            log.warn("Access denied: Holder credential {} is in unusable state {}", holderCredentialId, hc.status());
            throw new IllegalStateException("Holder credential is not active: " + hc.status());
        }

        // 3. Validate underlying authoritative credential lifecycle
        StoredCredential storedCredential = credentialStore.findById(hc.credentialId().value())
                .orElseThrow(() -> new CredentialNotFoundException(hc.credentialId().value()));

        if (storedCredential.status() != CredentialStatus.ACTIVE) {
            log.warn("Access denied: Authoritative credential {} is not ACTIVE (status: {})",
                    hc.credentialId(), storedCredential.status());
            throw new IllegalStateException("Authoritative credential is not active: " + storedCredential.status());
        }

        // 4. Validate credential digest integrity
        if (!MessageDigest.isEqual(hc.credentialDigest(), storedCredential.digest())) {
            log.error("Integrity failure: Holder credential digest does not match stored credential digest for {}",
                    holderCredentialId);
            throw new IllegalStateException("Credential digest tampering or corruption detected");
        }

        // 5. Retrieve Attribute Commitment
        AttributeCommitment commitment = commitmentStore.findLatestByCredentialIdAndAttributeName(
                hc.credentialId().value(), attributeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No authenticated commitment found for credential " + hc.credentialId() + " and attribute " + attributeName));

        // 6. Retrieve Holder Proving Secret Opening Material
        AttributeOpeningSecret secret = provingMaterialStore.findByHolderAndCredentialAndAttribute(
                holderId.value(), hc.credentialId().value(), attributeName)
                .orElseThrow(() -> new IllegalStateException(
                        "No proving material found for holder " + holderId + " and attribute " + attributeName));

        // 7. Cryptographic continuity verification: C ?= vG + rH
        boolean openingValid = pedersenCommitmentService.verifyOpening(
                secret.attributeValue(), secret.blindingFactor(), commitment.commitment());

        if (!openingValid) {
            log.error("CRITICAL: Cryptographic opening verification failed for holder {} on attribute {}!",
                    holderId, attributeName);
            throw new IllegalStateException("Cryptographic commitment opening verification failed");
        }

        // 8. Verify blinding fingerprint consistency
        if (!MessageDigest.isEqual(secret.blindingFingerprint(), commitment.blindingFingerprint())) {
            log.error("CRITICAL: Blinding fingerprint mismatch for holder {} on attribute {}!",
                    holderId, attributeName);
            throw new IllegalStateException("Blinding factor fingerprint mismatch");
        }

        log.info("Successfully established HolderProofContext for holder={}, credential={}, attribute={}",
                holderId, hc.credentialId(), attributeName);

        return new HolderProofContext(
                holderCredentialId,
                hc.credentialId(),
                hc.credentialDigest(),
                attributeName,
                commitment.commitment(),
                commitment.attestation(),
                commitment.attestationKeyId(),
                secret.attributeValue(),
                secret.blindingFactor(),
                secret.blindingFingerprint(),
                PROTOCOL_VERSION
        );
    }
}

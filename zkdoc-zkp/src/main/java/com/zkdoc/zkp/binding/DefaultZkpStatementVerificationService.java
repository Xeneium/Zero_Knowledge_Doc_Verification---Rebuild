package com.zkdoc.zkp.binding;

import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.StoredCredential;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.zkp.AttributeCommitment;
import com.zkdoc.zkp.CommitmentStore;
import com.zkdoc.zkp.engine.AttributePredicate;
import com.zkdoc.zkp.engine.BulletproofPredicateEngine;
import com.zkdoc.zkp.engine.PredicateProofArtifact;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link ZkpStatementVerificationService}.
 * <p>
 * Enforces strict, server-side statement binding:
 * <ol>
 *   <li>Resolves authoritative {@link StoredCredential} from persistence.</li>
 *   <li>Validates credential status is ACTIVE and expiresAt > now.</li>
 *   <li>Re-verifies canonical digest matches SHA-256(canonicalPayload).</li>
 *   <li>Verifies issuer RSA-PSS signature against authoritative credential digest.</li>
 *   <li>Resolves authoritative {@link AttributeCommitment} from {@link CommitmentStore}.</li>
 *   <li>Verifies issuer commitment attestation signature (KeyRegistry or fallback signer).</li>
 *   <li>Constructs {@link TrustedVerificationContext} containing authoritative anchors.</li>
 *   <li>Passes authentic commitment point and proof to {@link BulletproofPredicateEngine}.</li>
 *   <li>Returns structured {@link ZkpStatementVerificationResult} without leaking secrets.</li>
 * </ol>
 */
@Service
public class DefaultZkpStatementVerificationService implements ZkpStatementVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultZkpStatementVerificationService.class);
    private static final String PROTOCOL_VERSION = "zkdoc-range-v1";

    private final CredentialStore credentialStore;
    private final CommitmentStore commitmentStore;
    private final CanonicalCredentialEncoder canonicalEncoder;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner issuerSigner;
    private final KeyRegistryService keyRegistryService;
    private final BulletproofPredicateEngine predicateEngine;

    @Autowired
    public DefaultZkpStatementVerificationService(
            CredentialStore credentialStore,
            CommitmentStore commitmentStore,
            CanonicalCredentialEncoder canonicalEncoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner issuerSigner,
            @Autowired(required = false) KeyRegistryService keyRegistryService,
            BulletproofPredicateEngine predicateEngine) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore must not be null");
        this.commitmentStore = Objects.requireNonNull(commitmentStore, "commitmentStore must not be null");
        this.canonicalEncoder = Objects.requireNonNull(canonicalEncoder, "canonicalEncoder must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.issuerSigner = Objects.requireNonNull(issuerSigner, "issuerSigner must not be null");
        this.keyRegistryService = keyRegistryService;
        this.predicateEngine = Objects.requireNonNull(predicateEngine, "predicateEngine must not be null");
    }

    @Override
    public TrustedVerificationContext resolveContext(
            CredentialId credentialId,
            String attributeName,
            AttributePredicate predicate,
            byte[] nonce) {

        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");

        StoredCredential credential = credentialStore.findById(credentialId.value())
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + credentialId));

        if (credential.status() != CredentialStatus.ACTIVE) {
            throw new IllegalStateException("Credential is not in ACTIVE state: " + credential.status());
        }
        if (credential.expiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Credential has expired: expiresAt=" + credential.expiresAt());
        }

        byte[] calculatedDigest = hasher.hash(credential.canonicalPayload());
        if (!MessageDigest.isEqual(calculatedDigest, credential.digest())) {
            log.error("Integrity error: Credential digest mismatch for credentialId={}", credentialId);
            throw new IllegalStateException("Credential digest tampering detected");
        }

        if (!issuerSigner.verify(credential.digest(), credential.signature())) {
            log.error("Authentication error: Credential issuer signature invalid for credentialId={}", credentialId);
            throw new IllegalStateException("Credential issuer signature verification failed");
        }

        AttributeCommitment storedCommitment = commitmentStore
                .findLatestByCredentialIdAndAttributeName(credentialId.value(), attributeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No authenticated commitment found for credential=" + credentialId + ", attribute=" + attributeName));

        if (!hasValidIssuerAttestation(storedCommitment)) {
            log.error("Attestation error: Commitment attestation invalid for credentialId={}, attribute={}",
                    credentialId, attributeName);
            throw new IllegalStateException("Commitment attestation signature verification failed");
        }

        return new TrustedVerificationContext(
                credentialId,
                credential.digest(),
                predicate,
                storedCommitment.commitment(),
                storedCommitment.attestation(),
                storedCommitment.attestationKeyId(),
                "default-issuer",
                nonce,
                PROTOCOL_VERSION
        );
    }

    @Override
    public ZkpStatementVerificationResult verify(ZkpVerificationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 1. Validate protocol version
        if (!PROTOCOL_VERSION.equals(request.proofVersion())) {
            log.warn("Verification rejected: unsupported protocol version '{}'", request.proofVersion());
            return ZkpStatementVerificationResult.failure(
                    VerificationOutcomeStatus.UNSUPPORTED_VERSION,
                    request.credentialId(),
                    request.toPredicate(),
                    "Unsupported proof version: " + request.proofVersion()
            );
        }

        // 2. Validate proof artifact bounds
        PredicateProofArtifact artifact = request.proofArtifact();
        if (artifact.proofBytes().length == 0 || artifact.proofBytes().length > PredicateProofArtifact.MAX_PROOF_BYTES) {
            return ZkpStatementVerificationResult.failure(
                    VerificationOutcomeStatus.INVALID_PROOF,
                    request.credentialId(),
                    request.toPredicate(),
                    "Proof artifact size outside acceptable boundaries"
            );
        }

        // 3. Resolve trusted verification context from authoritative server-side state
        TrustedVerificationContext context;
        try {
            context = resolveContext(
                    request.credentialId(),
                    request.attributeName(),
                    request.toPredicate(),
                    request.verifierNonce()
            );
        } catch (IllegalArgumentException e) {
            log.warn("Verification failed during context resolution (bad request): {}", e.getMessage());
            return ZkpStatementVerificationResult.failure(
                    VerificationOutcomeStatus.INVALID_CREDENTIAL,
                    request.credentialId(),
                    request.toPredicate(),
                    e.getMessage()
            );
        } catch (IllegalStateException e) {
            log.warn("Verification failed during context resolution (state/crypto invalid): {}", e.getMessage());
            String msg = e.getMessage().toLowerCase();
            VerificationOutcomeStatus status = msg.contains("expire") || msg.contains("active")
                    ? VerificationOutcomeStatus.EXPIRED_OR_REVOKED_CREDENTIAL
                    : (msg.contains("commitment") ? VerificationOutcomeStatus.INVALID_COMMITMENT : VerificationOutcomeStatus.INVALID_CREDENTIAL);
            return ZkpStatementVerificationResult.failure(
                    status,
                    request.credentialId(),
                    request.toPredicate(),
                    e.getMessage()
            );
        }

        // 4. Cryptographic Proof Verification using server-resolved commitment point
        boolean cryptographicResult = predicateEngine.verifyGreaterThanOrEqual(
                context.authenticatedCommitmentPoint(),
                request.threshold(),
                context.verifierNonce(),
                artifact
        );

        if (!cryptographicResult) {
            log.debug("Cryptographic Bulletproof verification failed for credentialId={}, attribute={}",
                    request.credentialId(), request.attributeName());
            return ZkpStatementVerificationResult.failure(
                    VerificationOutcomeStatus.INVALID_PROOF,
                    request.credentialId(),
                    request.toPredicate(),
                    "Cryptographic proof verification failed"
            );
        }

        log.info("ZKP statement verification successfully passed for credentialId={}, attribute={}, threshold={}",
                request.credentialId(), request.attributeName(), request.threshold());

        return ZkpStatementVerificationResult.valid(request.credentialId(), request.toPredicate());
    }

    private boolean hasValidIssuerAttestation(AttributeCommitment commitment) {
        byte[] expectedPayload = canonicalEncoder.encodeCommitmentAttestation(
                commitment.credentialId(), commitment.attributeName(), commitment.commitment());
        if (!Arrays.equals(expectedPayload, commitment.attestationPayload())) {
            return false;
        }

        byte[] digest = hasher.hash(expectedPayload);

        // First attempt verification against KeyRegistryService if present
        if (keyRegistryService != null) {
            if (commitment.attestationKeyId() != null) {
                if (keyRegistryService.verify(
                        commitment.attestationKeyId(),
                        KeyPurpose.COMMITMENT_ATTESTATION_SIGNING,
                        null,
                        digest,
                        commitment.attestation())) {
                    return true;
                }
            }
        }

        // Fallback to issuerSigner
        if (issuerSigner != null) {
            return issuerSigner.verify(digest, commitment.attestation());
        }

        return false;
    }
}

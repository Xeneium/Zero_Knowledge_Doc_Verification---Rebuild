package com.zkdoc.zkp.binding;

import com.zkdoc.credential.CredentialId;
import com.zkdoc.zkp.engine.AttributePredicate;
import com.zkdoc.zkp.engine.PredicateOperator;
import com.zkdoc.zkp.engine.PredicateProofArtifact;
import java.util.Objects;
import java.util.UUID;

/**
 * Caller verification request submitted to the ZKP verification service.
 * <p>
 * CRITICAL CALLER-CONTROLLED DATA BOUNDARY:
 * Contains ONLY public identifiers, statement claim, freshness challenge nonce, and proof artifact:
 * - proofVersion
 * - credentialId
 * - attributeName
 * - operator (e.g. ">=")
 * - threshold
 * - verifierNonce
 * - proofArtifact
 * <p>
 * ABSOLUTELY FORBIDDEN FROM CALLER:
 * - caller-supplied commitment point (server authoritatively resolves from stored Phase 5 state)
 * - caller-supplied credential digest (server reconstructs from canonical payload and issuer signature)
 * - caller-supplied issuer ID / key ID
 * - caller-supplied attestation signature
 * - caller-supplied raw attribute value / witness / blinding factor
 */
public record ZkpVerificationRequest(
        String proofVersion,
        CredentialId credentialId,
        String attributeName,
        PredicateOperator operator,
        long threshold,
        byte[] verifierNonce,
        PredicateProofArtifact proofArtifact
) {
    public ZkpVerificationRequest {
        Objects.requireNonNull(proofVersion, "proofVersion must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        if (attributeName.isBlank()) {
            throw new IllegalArgumentException("attributeName must not be blank");
        }
        Objects.requireNonNull(operator, "operator must not be null");
        if (threshold < 0) {
            throw new IllegalArgumentException("Negative threshold is not supported: " + threshold);
        }
        Objects.requireNonNull(verifierNonce, "verifierNonce must not be null");
        if (verifierNonce.length == 0) {
            throw new IllegalArgumentException("verifierNonce must not be empty");
        }
        Objects.requireNonNull(proofArtifact, "proofArtifact must not be null");

        verifierNonce = verifierNonce.clone();
    }

    @Override
    public byte[] verifierNonce() {
        return verifierNonce.clone();
    }

    public AttributePredicate toPredicate() {
        return new AttributePredicate(attributeName, operator, threshold);
    }
}

package com.zkdoc.zkp.holder;

import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.zkp.engine.AttributePredicate;
import com.zkdoc.zkp.engine.PredicateOperator;
import java.util.Objects;

/**
 * Strongly-typed request DTO for holder predicate proof generation.
 * <p>
 * Contains ONLY holder-controlled identifiers and predicate parameters.
 * <p>
 * SECURITY INVARIANT:
 * Caller NEVER provides raw attributes, blinding factors, commitments,
 * credential digests, or issuer attestations. Those are authoritatively
 * resolved by the system inside the secure proving boundary.
 */
public record HolderPredicateProofRequest(
        HolderCredentialId holderCredentialId,
        String attributeName,
        PredicateOperator operator,
        long threshold,
        byte[] transcriptNonce
) {
    public HolderPredicateProofRequest {
        Objects.requireNonNull(holderCredentialId, "holderCredentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        if (attributeName.isBlank()) {
            throw new IllegalArgumentException("attributeName must not be blank");
        }
        Objects.requireNonNull(operator, "operator must not be null");
        if (threshold < 0) {
            throw new IllegalArgumentException("Negative threshold is not supported: " + threshold);
        }
        Objects.requireNonNull(transcriptNonce, "transcriptNonce must not be null");
        if (transcriptNonce.length == 0) {
            throw new IllegalArgumentException("transcriptNonce must not be empty");
        }
        transcriptNonce = transcriptNonce.clone();
    }

    @Override
    public byte[] transcriptNonce() {
        return transcriptNonce.clone();
    }

    public AttributePredicate toPredicate() {
        return new AttributePredicate(attributeName, operator, threshold);
    }
}

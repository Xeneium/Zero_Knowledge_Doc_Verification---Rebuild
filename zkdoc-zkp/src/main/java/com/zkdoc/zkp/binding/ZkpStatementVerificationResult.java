package com.zkdoc.zkp.binding;

import com.zkdoc.credential.CredentialId;
import com.zkdoc.zkp.engine.AttributePredicate;
import java.util.Objects;

/**
 * Structured verification result distinguishing proof validity, credential authenticity,
 * commitment authenticity, and lifecycle status.
 * <p>
 * MINIMAL DISCLOSURE:
 * Contains only protocol-approved verification results, predicate claim, and outcome status.
 * NEVER leaks raw attributes or witness secrets.
 */
public record ZkpStatementVerificationResult(
        boolean verified,
        VerificationOutcomeStatus status,
        CredentialId credentialId,
        AttributePredicate predicate,
        String message
) {
    public ZkpStatementVerificationResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static ZkpStatementVerificationResult valid(CredentialId credentialId, AttributePredicate predicate) {
        return new ZkpStatementVerificationResult(true, VerificationOutcomeStatus.VALID, credentialId, predicate, "Verification successful");
    }

    public static ZkpStatementVerificationResult failure(VerificationOutcomeStatus status, CredentialId credentialId, AttributePredicate predicate, String message) {
        return new ZkpStatementVerificationResult(false, status, credentialId, predicate, message);
    }
}

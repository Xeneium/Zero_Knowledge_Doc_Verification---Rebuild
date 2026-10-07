package com.zkdoc.zkp.binding;

import com.zkdoc.credential.CredentialId;

/**
 * Service SPI governing the resolution of trusted verification context and
 * the end-to-end evaluation of ZKP predicate statements.
 * <p>
 * Decouples the cryptographic verification engine from persistence, web controllers,
 * and caller-supplied parameters.
 */
public interface ZkpStatementVerificationService {

    /**
     * Resolves the authoritative verification context for a credential and attribute.
     * Validates credential existence, lifecycle (ACTIVE), canonical digest, issuer signature,
     * commitment existence, and issuer attestation signature.
     *
     * @param credentialId Credential identifier
     * @param attributeName Credential attribute name
     * @param predicate Predicate constraint
     * @param nonce Verifier challenge nonce
     * @return Authoritative {@link TrustedVerificationContext}
     * @throws IllegalArgumentException if inputs are invalid or attribute does not exist
     * @throws IllegalStateException if credential/commitment lifecycle or signatures fail
     */
    TrustedVerificationContext resolveContext(
            CredentialId credentialId,
            String attributeName,
            com.zkdoc.zkp.engine.AttributePredicate predicate,
            byte[] nonce);

    /**
     * Executes end-to-end statement binding and Bulletproof proof verification.
     *
     * @param request Caller verification request
     * @return Structured {@link ZkpStatementVerificationResult}
     */
    ZkpStatementVerificationResult verify(ZkpVerificationRequest request);
}

package com.zkdoc.zkp.binding;

public enum VerificationOutcomeStatus {
    /** The predicate proof is valid and fully bound to active, authentic credential and commitment state. */
    VALID,

    /** The cryptographic Bulletproof proof failed verification or does not satisfy predicate constraints. */
    INVALID_PROOF,

    /** The credential record does not exist or has an invalid canonical digest or issuer signature. */
    INVALID_CREDENTIAL,

    /** The credential has expired or been revoked/suspended. */
    EXPIRED_OR_REVOKED_CREDENTIAL,

    /** No authenticated commitment found or issuer attestation signature check failed. */
    INVALID_COMMITMENT,

    /** The signing key or attestation key is revoked, untrusted, or missing required purpose. */
    UNTRUSTED_KEY,

    /** The requested attribute does not exist in the credential or is not supported. */
    ATTRIBUTE_NOT_FOUND,

    /** The protocol/proof version or bit range is unsupported. */
    UNSUPPORTED_VERSION,

    /** Malformed inputs, negative thresholds, or invalid verifier nonce context. */
    INVALID_CONTEXT
}

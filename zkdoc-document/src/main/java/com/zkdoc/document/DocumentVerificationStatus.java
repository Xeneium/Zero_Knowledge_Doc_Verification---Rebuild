package com.zkdoc.document;

/**
 * Standard cryptographic verification outcome statuses for direct document verification.
 */
public enum DocumentVerificationStatus {
    VERIFIED,
    INTEGRITY_MISMATCH,
    INVALID_SIGNATURE,
    REVOKED,
    EXPIRED,
    NOT_FOUND,
    UNABLE_TO_VERIFY
}

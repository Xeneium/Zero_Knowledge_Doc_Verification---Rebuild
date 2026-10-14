package com.zkdoc.api.audit;

/**
 * Data privacy classification model governing what information may be retained,
 * audited, logged, or returned across system boundaries.
 */
public enum PrivacyClassification {
    /**
     * Public information: protocol versions, public key fingerprints, non-secret capability identifiers.
     */
    PUBLIC,

    /**
     * Internal operational metadata: internal entity IDs, timestamps, status enums, outcome codes.
     */
    INTERNAL,

    /**
     * Sensitive metadata: principal subjects, hashed client IP addresses, correlation IDs.
     */
    SENSITIVE,

    /**
     * Prohibited secret data: passwords, JWTs, private keys, raw document bytes, raw attributes,
     * blinding factors, witnesses, proof bytes, QR capability bearer tokens.
     * MUST NEVER REACH LOGS OR PERSISTENT AUDIT TRAILS.
     */
    SECRET
}

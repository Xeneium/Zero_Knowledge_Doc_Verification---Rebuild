package com.zkdoc.keys;

/**
 * Explicit cryptographic key purpose taxonomy.
 * A key authorized for one purpose cannot be used or trusted for any other purpose.
 */
public enum KeyPurpose {
    /** Cryptographic signing and validation of API Bearer JWTs. */
    JWT_SIGNING,

    /** Authoritative RSASSA-PSS signing of verifiable credentials. */
    CREDENTIAL_SIGNING,

    /** RSASSA-PSS signing of physical document records and evidence. */
    DOCUMENT_SIGNING,

    /** RSASSA-PSS signing of Pedersen attribute commitment attestations. */
    COMMITMENT_ATTESTATION_SIGNING;

    /**
     * Verifies that this purpose allows the requested operational action.
     */
    public boolean matches(KeyPurpose requestedPurpose) {
        return this == requestedPurpose;
    }
}

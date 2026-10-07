package com.zkdoc.keys;

/**
 * Categorization of key ownership and trust domain.
 * Strictly separates application users from cryptographic authorities.
 */
public enum KeyOwnerType {
    /** Platform-level trust authority (e.g. system JWT signing or platform anchors). */
    PLATFORM,

    /** Regulated or trusted credential issuer entity. */
    ISSUER,

    /** Internal platform microservice or backend component. */
    SERVICE,

    /** Dedicated security domain or hardware security enclave. */
    SECURITY_DOMAIN
}

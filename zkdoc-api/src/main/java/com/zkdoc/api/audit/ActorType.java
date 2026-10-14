package com.zkdoc.api.audit;

/**
 * Categorization of actors initiating security-relevant operations.
 */
public enum ActorType {
    USER,
    ISSUER,
    HOLDER,
    VERIFIER,
    ADMIN,
    SYSTEM,
    ANONYMOUS_PUBLIC
}

package com.zkdoc.credential;

/**
 * Explicit lifecycle state machine for verifiable credentials.
 * Decouples operational/administrative state from digital signature validity.
 */
public enum CredentialStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    REVOKED;

    /**
     * Verifies whether transitioning from the current state to the target state is permissible.
     */
    public boolean canTransitionTo(CredentialStatus next) {
        if (this == next) return true;
        return switch (this) {
            case DRAFT -> next == ACTIVE || next == REVOKED;
            case ACTIVE -> next == SUSPENDED || next == REVOKED || next == EXPIRED;
            case SUSPENDED -> next == ACTIVE || next == REVOKED || next == EXPIRED;
            case EXPIRED -> next == REVOKED;
            case REVOKED -> false; // Irreversible terminal state
        };
    }

    /**
     * Indicates whether a credential in this state is operationally usable for verification.
     */
    public boolean isVerifiable() {
        return this == ACTIVE;
    }
}

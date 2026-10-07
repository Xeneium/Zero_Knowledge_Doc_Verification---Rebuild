package com.zkdoc.credential;

/**
 * Explicit operational lifecycle state machine for holder credentials.
 * Decouples the holder's usage relationship from the issuer's cryptographic credential status.
 */
public enum HolderCredentialStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED,
    EXPIRED;

    /**
     * Verifies whether transitioning from the current state to the target state is permissible.
     */
    public boolean canTransitionTo(HolderCredentialStatus next) {
        if (this == next) return true;
        return switch (this) {
            case ACTIVE -> next == SUSPENDED || next == REVOKED || next == EXPIRED;
            case SUSPENDED -> next == ACTIVE || next == REVOKED || next == EXPIRED;
            case EXPIRED -> next == REVOKED;
            case REVOKED -> false; // Irreversible terminal state
        };
    }

    /**
     * Indicates whether the holder credential is operationally usable for holder workflows and proving.
     */
    public boolean isUsable() {
        return this == ACTIVE;
    }
}

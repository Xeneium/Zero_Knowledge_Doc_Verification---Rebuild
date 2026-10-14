package com.zkdoc.credential;

import java.util.UUID;

/**
 * Exception thrown when an illegal credential lifecycle state transition is requested.
 */
public class InvalidLifecycleTransitionException extends RuntimeException {

    private final UUID credentialId;
    private final CredentialStatus currentStatus;
    private final CredentialStatus attemptedStatus;

    public InvalidLifecycleTransitionException(UUID credentialId, CredentialStatus currentStatus, CredentialStatus attemptedStatus) {
        super(String.format("Cannot transition credential %s from %s to %s", credentialId, currentStatus, attemptedStatus));
        this.credentialId = credentialId;
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public InvalidLifecycleTransitionException(String message) {
        super(message);
        this.credentialId = null;
        this.currentStatus = null;
        this.attemptedStatus = null;
    }

    public UUID credentialId() {
        return credentialId;
    }

    public CredentialStatus currentStatus() {
        return currentStatus;
    }

    public CredentialStatus attemptedStatus() {
        return attemptedStatus;
    }
}

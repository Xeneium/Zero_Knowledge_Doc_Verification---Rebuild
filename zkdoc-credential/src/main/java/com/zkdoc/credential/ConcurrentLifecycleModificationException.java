package com.zkdoc.credential;

import java.util.UUID;

/**
 * Exception thrown when an optimistic concurrency conflict occurs during a lifecycle state transition.
 */
public class ConcurrentLifecycleModificationException extends RuntimeException {

    private final UUID credentialId;

    public ConcurrentLifecycleModificationException(UUID credentialId) {
        super(String.format("Concurrent modification detected for credential %s: optimistic lock version mismatch", credentialId));
        this.credentialId = credentialId;
    }

    public UUID credentialId() {
        return credentialId;
    }
}

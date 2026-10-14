package com.zkdoc.credential;

import java.util.List;
import java.util.UUID;

/**
 * Store interface for persisting and querying immutable credential lifecycle events.
 */
public interface CredentialLifecycleEventStore {

    void record(CredentialLifecycleEvent event);

    List<CredentialLifecycleEvent> findByCredentialId(UUID credentialId);
}

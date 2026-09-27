package com.zkdoc.credential;

import java.util.Optional;
import java.util.UUID;

public interface CredentialStore {
    void save(SignedCredential credential);

    Optional<StoredCredential> findById(UUID credentialId);
}

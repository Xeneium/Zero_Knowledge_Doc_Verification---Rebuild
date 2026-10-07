package com.zkdoc.credential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage SPI contract for holder credentials.
 */
public interface HolderCredentialStore {

    void save(HolderCredential holderCredential);

    Optional<HolderCredential> findById(HolderCredentialId id);

    Optional<HolderCredential> findByHolderIdAndCredentialId(HolderId holderId, CredentialId credentialId);

    List<HolderCredential> findAllByHolderId(HolderId holderId);

    void updateStatus(HolderCredentialId id, HolderCredentialStatus status);

    boolean existsByHolderIdAndCredentialId(HolderId holderId, CredentialId credentialId);
}

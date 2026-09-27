package com.zkdoc.zkp;

import java.util.Optional;
import java.util.UUID;

public interface CommitmentStore {
    boolean blindingFingerprintExists(byte[] fingerprint);

    void save(AttributeCommitment commitment);

    Optional<AttributeCommitment> findLatestByCredentialIdAndAttributeName(UUID credentialId, String attributeName);
}

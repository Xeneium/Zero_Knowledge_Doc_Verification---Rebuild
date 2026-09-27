package com.zkdoc.keys;

import java.util.Optional;

public interface KeyRegistry {
    Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose);

    Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose);

    void register(RegisteredKey key);

    void updateStatus(String keyId, KeyStatus status);
}

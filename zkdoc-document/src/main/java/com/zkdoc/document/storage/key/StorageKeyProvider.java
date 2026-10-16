package com.zkdoc.document.storage.key;

import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Key custody provider for document artifact envelope encryption at rest.
 * Supports active key rotation and multi-key decryption lookup.
 */
public interface StorageKeyProvider {

    /**
     * Returns the identifier of the active key used to encrypt new artifacts.
     */
    String getActiveKeyId();

    /**
     * Resolves the secret key corresponding to the supplied keyId.
     *
     * @param keyId Unique key identifier stored in artifact envelope header
     * @return SecretKey if known, or empty
     */
    Optional<SecretKey> getKey(String keyId);
}

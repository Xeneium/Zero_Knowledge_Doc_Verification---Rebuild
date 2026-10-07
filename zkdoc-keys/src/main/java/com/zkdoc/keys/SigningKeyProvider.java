package com.zkdoc.keys;

import java.security.PrivateKey;
import java.util.Optional;

/**
 * Custody abstraction for cryptographic private signing keys.
 * Separates public trust registry metadata from private key custody.
 * Implementations may be local development providers, HashiCorp Vault, AWS KMS, or PKCS#11 HSMs.
 */
public interface SigningKeyProvider {

    /**
     * Resolves the private key for the given key identifier and purpose.
     *
     * @param keyId Unique key identifier
     * @param purpose Explicit cryptographic purpose
     * @return Optional containing the private key if held locally, or empty
     */
    Optional<PrivateKey> getPrivateKey(String keyId, KeyPurpose purpose);

    /**
     * Checks if the provider possesses signing capability for the given key and purpose.
     */
    boolean hasPrivateKey(String keyId, KeyPurpose purpose);
}

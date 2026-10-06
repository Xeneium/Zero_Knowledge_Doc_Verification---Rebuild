package com.zkdoc.core.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Optional;

/**
 * Abstraction for loading cryptographic public and private keys from
 * secure storage (KMS, HSM, local secure files, or key registries).
 */
public interface KeyProvider {

    /**
     * Retrieves the public key associated with the given key identifier.
     *
     * @param keyId Unique key identifier (e.g., UUID or JWT kid)
     * @return Resolved Public Key
     * @throws IllegalArgumentException if the key does not exist or cannot be parsed
     */
    PublicKey getPublicKey(String keyId);

    /**
     * Retrieves the private key associated with the key identifier if held locally.
     * In KMS/HSM deployments, private keys cannot be exported and signing is performed via remote API.
     *
     * @param keyId Unique key identifier
     * @return Optional containing the private key, or empty if externalized/non-exportable
     */
    Optional<PrivateKey> getPrivateKey(String keyId);
}

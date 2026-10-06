package com.zkdoc.core.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Contract for digital signature generation and verification.
 * Standard implementation mandates RSASSA-PSS with SHA-256 and 32-byte salt.
 */
public interface SignatureService {

    /**
     * Signs a pre-computed 32-byte digest using RSASSA-PSS.
     *
     * @param digest 32-byte SHA-256 digest
     * @param privateKey Authorized RSA private signing key
     * @return Raw signature byte array
     */
    byte[] signRsaPss(byte[] digest, PrivateKey privateKey);

    /**
     * Verifies an RSASSA-PSS digital signature against a 32-byte digest.
     *
     * @param digest 32-byte SHA-256 digest
     * @param signature Digital signature bytes to verify
     * @param publicKey Public key associated with the claimed signer
     * @return true if and only if the signature is mathematically valid
     */
    boolean verifyRsaPss(byte[] digest, byte[] signature, PublicKey publicKey);
}

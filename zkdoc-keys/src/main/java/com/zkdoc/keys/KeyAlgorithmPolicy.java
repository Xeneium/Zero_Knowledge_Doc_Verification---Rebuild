package com.zkdoc.keys;

import java.security.PublicKey;
import java.security.interfaces.RSAKey;

/**
 * Strict algorithm and parameter validation policy enforcing the platform's
 * cryptographic security baseline established in Phase 0 (ADR-006).
 */
public final class KeyAlgorithmPolicy {

    public static final String MANDATED_ALGORITHM = "RSASSA-PSS";
    public static final String MANDATED_PARAMETERS = "SHA-256/MGF1-SHA256/salt=32";
    public static final int MINIMUM_RSA_KEY_SIZE = 2048;
    public static final int RECOMMENDED_RSA_KEY_SIZE = 3072;

    private KeyAlgorithmPolicy() {
    }

    /**
     * Validates whether a key algorithm and parameter configuration complies with policy.
     */
    public static void validateAlgorithm(String algorithm, String parameters) {
        if (!MANDATED_ALGORITHM.equalsIgnoreCase(algorithm) && !"RSA".equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException(
                    "Unsupported signature algorithm: '" + algorithm + "'. Mandated: " + MANDATED_ALGORITHM);
        }
        if (parameters != null && !parameters.isBlank() && !MANDATED_PARAMETERS.equalsIgnoreCase(parameters)) {
            throw new IllegalArgumentException(
                    "Incompatible algorithm parameters: '" + parameters + "'. Mandated: " + MANDATED_PARAMETERS);
        }
    }

    /**
     * Validates public key type and modulus size.
     */
    public static void validatePublicKey(PublicKey publicKey) {
        if (!(publicKey instanceof RSAKey rsaKey)) {
            throw new IllegalArgumentException("Expected RSA public key, received: " + publicKey.getAlgorithm());
        }
        int bitLength = rsaKey.getModulus().bitLength();
        if (bitLength < MINIMUM_RSA_KEY_SIZE) {
            throw new IllegalArgumentException(
                    "RSA key size must be at least " + MINIMUM_RSA_KEY_SIZE + " bits, but found: " + bitLength);
        }
    }
}

package com.zkdoc.keys;

import com.zkdoc.core.crypto.RsaPssSignatureService;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory signing key provider for local development and integration testing.
 * Enforces strict keypair consistency verification on registration:
 * fails closed if the supplied private key does not match the registered public key.
 */
@Component
public class DevelopmentSigningKeyProvider implements SigningKeyProvider {

    private final Map<KeyScope, PrivateKey> privateKeys = new ConcurrentHashMap<>();
    private final RsaPssSignatureService signatureService = new RsaPssSignatureService();

    /**
     * Registers a private signing key for a specific keyId and purpose, cryptographically
     * validating that it matches the corresponding public key.
     *
     * @param keyId Unique key identifier
     * @param purpose Explicit cryptographic purpose
     * @param privateKey The RSA private key
     * @param expectedPublicKey The expected RSA public key to test against
     * @throws IllegalStateException if privateKey does not correspond to expectedPublicKey
     */
    public void registerKey(String keyId, KeyPurpose purpose, PrivateKey privateKey, PublicKey expectedPublicKey) {
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(expectedPublicKey, "expectedPublicKey must not be null");

        KeyAlgorithmPolicy.validatePublicKey(expectedPublicKey);

        // Verify keypair consistency via sign/verify test probe
        byte[] testNonce = new byte[32];
        for (int i = 0; i < testNonce.length; i++) {
            testNonce[i] = (byte) (i ^ 0x5A);
        }

        byte[] testSignature = signatureService.signRsaPss(testNonce, privateKey);
        boolean matches = signatureService.verifyRsaPss(testNonce, testSignature, expectedPublicKey);
        if (!matches) {
            throw new IllegalStateException(
                    "Security violation: Configured private key does not correspond to registered public key for keyId: " + keyId);
        }

        privateKeys.put(new KeyScope(keyId, purpose), privateKey);
    }

    @Override
    public Optional<PrivateKey> getPrivateKey(String keyId, KeyPurpose purpose) {
        if (keyId == null || purpose == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(privateKeys.get(new KeyScope(keyId, purpose)));
    }

    @Override
    public boolean hasPrivateKey(String keyId, KeyPurpose purpose) {
        if (keyId == null || purpose == null) {
            return false;
        }
        return privateKeys.containsKey(new KeyScope(keyId, purpose));
    }

    public void clear() {
        privateKeys.clear();
    }

    private record KeyScope(String keyId, KeyPurpose purpose) {}
}

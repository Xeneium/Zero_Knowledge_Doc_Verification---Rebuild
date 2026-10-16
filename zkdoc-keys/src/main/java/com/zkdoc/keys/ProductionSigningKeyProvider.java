package com.zkdoc.keys;

import com.zkdoc.core.crypto.RsaPssSignatureService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready PKCS#8 signing key provider (FIND-004).
 * Enforces:
 * 1. Explicit key loading from PKCS#8 PEM, DER byte array, or secure filesystem paths.
 * 2. Cryptographic keypair consistency probe: rejects any private key that does not mathematically
 *    correspond to the registered public key.
 * 3. Strict fail-closed semantics: no ephemeral runtime key generation.
 */
public class ProductionSigningKeyProvider implements SigningKeyProvider {

    private final Map<KeyScope, PrivateKey> keyStore = new ConcurrentHashMap<>();
    private final RsaPssSignatureService signatureService = new RsaPssSignatureService();

    /**
     * Loads and registers a PKCS#8 PEM or Base64 encoded private key string.
     */
    public void loadPemKey(String keyId, KeyPurpose purpose, String pemContent, PublicKey expectedPublicKey) {
        Objects.requireNonNull(pemContent, "pemContent must not be null");
        String sanitized = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] derBytes = Base64.getDecoder().decode(sanitized);
        loadDerKey(keyId, purpose, derBytes, expectedPublicKey);
    }

    /**
     * Loads a PKCS#8 encoded private key from a secure local filesystem path.
     */
    public void loadKeyFromFile(String keyId, KeyPurpose purpose, Path keyPath, PublicKey expectedPublicKey) throws IOException {
        Objects.requireNonNull(keyPath, "keyPath must not be null");
        if (!Files.exists(keyPath) || !Files.isRegularFile(keyPath)) {
            throw new IllegalArgumentException("Private key file does not exist or is not a regular file: " + keyPath);
        }

        byte[] content = Files.readAllBytes(keyPath);
        String text = new String(content).trim();
        if (text.startsWith("-----BEGIN")) {
            loadPemKey(keyId, purpose, text, expectedPublicKey);
        } else {
            loadDerKey(keyId, purpose, content, expectedPublicKey);
        }
    }

    /**
     * Loads and registers a DER encoded PKCS#8 private key, verifying keypair consistency.
     */
    public void loadDerKey(String keyId, KeyPurpose purpose, byte[] derBytes, PublicKey expectedPublicKey) {
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(derBytes, "derBytes must not be null");
        Objects.requireNonNull(expectedPublicKey, "expectedPublicKey must not be null");

        KeyAlgorithmPolicy.validatePublicKey(expectedPublicKey);

        PrivateKey privateKey;
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(derBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid PKCS#8 RSA private key encoding for keyId: " + keyId, e);
        }

        // Verify keypair consistency via sign/verify test probe
        byte[] probeNonce = new byte[32];
        for (int i = 0; i < probeNonce.length; i++) {
            probeNonce[i] = (byte) (i ^ 0xA5);
        }

        byte[] probeSignature = signatureService.signRsaPss(probeNonce, privateKey);
        boolean valid = signatureService.verifyRsaPss(probeNonce, probeSignature, expectedPublicKey);
        if (!valid) {
            throw new IllegalStateException(
                    "Security violation: Configured production private key does not correspond to expected public key for keyId: " + keyId);
        }

        keyStore.put(new KeyScope(keyId, purpose), privateKey);
    }

    @Override
    public Optional<PrivateKey> getPrivateKey(String keyId, KeyPurpose purpose) {
        if (keyId == null || purpose == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(keyStore.get(new KeyScope(keyId, purpose)));
    }

    @Override
    public boolean hasPrivateKey(String keyId, KeyPurpose purpose) {
        if (keyId == null || purpose == null) {
            return false;
        }
        return keyStore.containsKey(new KeyScope(keyId, purpose));
    }

    public int getLoadedKeyCount() {
        return keyStore.size();
    }

    private record KeyScope(String keyId, KeyPurpose purpose) {}
}

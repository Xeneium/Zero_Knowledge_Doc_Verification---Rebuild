package com.zkdoc.core.crypto;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Objects;

/**
 * Standard RSASSA-PSS digital signature service implementing RFC 8017 / PKCS #1 v2.2.
 * Enforces SHA-256 digest, MGF1 with SHA-256, 32-byte salt length, and trailer field 1.
 */
public class RsaPssSignatureService implements SignatureService {

    public static final String ALGORITHM = "RSASSA-PSS";
    public static final String DIGEST_ALGORITHM = "SHA-256";
    public static final String MGF1_ALGORITHM = "MGF1";
    public static final int SALT_LENGTH = 32;
    public static final int TRAILER_FIELD = 1;
    public static final int MINIMUM_KEY_SIZE_BITS = 2048;

    public static final PSSParameterSpec PSS_PARAMETER_SPEC = new PSSParameterSpec(
            DIGEST_ALGORITHM,
            MGF1_ALGORITHM,
            MGF1ParameterSpec.SHA256,
            SALT_LENGTH,
            TRAILER_FIELD);

    @Override
    public byte[] signRsaPss(byte[] digest, PrivateKey privateKey) {
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");

        if (digest.length != 32) {
            throw new IllegalArgumentException("RSASSA-PSS requires 32-byte SHA-256 digest, received: " + digest.length);
        }
        validateKeySize(privateKey);

        try {
            Signature signature = createSignatureInstance();
            signature.initSign(privateKey);
            signature.update(digest);
            return signature.sign();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to generate RSASSA-PSS signature", exception);
        }
    }

    @Override
    public boolean verifyRsaPss(byte[] digest, byte[] signature, PublicKey publicKey) {
        if (digest == null || signature == null || publicKey == null) {
            return false;
        }
        if (digest.length != 32 || signature.length == 0) {
            return false;
        }

        try {
            validateKeySize(publicKey);
            Signature verifier = createSignatureInstance();
            verifier.initVerify(publicKey);
            verifier.update(digest);
            return verifier.verify(signature);
        } catch (SignatureException | InvalidKeyException exception) {
            // Malformed, truncated, or incompatible signature encodings fail verification gracefully
            return false;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unexpected cryptographic failure during signature verification", exception);
        }
    }

    private Signature createSignatureInstance() throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.setParameter(PSS_PARAMETER_SPEC);
        return signature;
    }

    private void validateKeySize(RSAKey rsaKey) {
        int keyLength = rsaKey.getModulus().bitLength();
        if (keyLength < MINIMUM_KEY_SIZE_BITS) {
            throw new IllegalArgumentException(
                    "RSA key size must be at least " + MINIMUM_KEY_SIZE_BITS + " bits, but found: " + keyLength);
        }
    }

    private void validateKeySize(Object key) {
        if (key instanceof RSAKey rsaKey) {
            validateKeySize(rsaKey);
        }
    }
}

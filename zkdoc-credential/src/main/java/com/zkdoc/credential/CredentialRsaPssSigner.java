package com.zkdoc.credential;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import org.springframework.stereotype.Component;

@Component
public class CredentialRsaPssSigner {
    private static final PSSParameterSpec PSS_SHA_256 = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
    private final KeyPair keyPair = generateKeyPair();

    public byte[] sign(byte[] digest) {
        try {
            Signature signature = signature();
            signature.initSign(keyPair.getPrivate());
            signature.update(digest);
            return signature.sign();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign credential digest", exception);
        }
    }

    public boolean verify(byte[] digest, byte[] value) {
        try {
            Signature signature = signature();
            signature.initVerify(keyPair.getPublic());
            signature.update(digest);
            return signature.verify(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify credential signature", exception);
        }
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate credential RSA key pair", exception);
        }
    }

    private Signature signature() throws GeneralSecurityException {
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS_SHA_256);
        return signature;
    }
}

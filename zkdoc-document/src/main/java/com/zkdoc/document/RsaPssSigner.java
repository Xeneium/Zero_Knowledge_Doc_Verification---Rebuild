package com.zkdoc.document;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import org.springframework.stereotype.Component;

/** A process-local RSA key pair is created when this Spring singleton starts. */
@Component
public class RsaPssSigner {
    private static final PSSParameterSpec PSS_SHA_256 = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
    private final KeyPair keyPair;

    public RsaPssSigner() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            keyPair = generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate the startup RSA key pair", exception);
        }
    }

    public byte[] sign(byte[] digest) {
        try {
            Signature signer = newSignature();
            signer.initSign(keyPair.getPrivate());
            signer.update(digest);
            return signer.sign();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign document digest", exception);
        }
    }

    public boolean verify(byte[] digest, byte[] signature) {
        try {
            Signature verifier = newSignature();
            verifier.initVerify(keyPair.getPublic());
            verifier.update(digest);
            return verifier.verify(signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify document signature", exception);
        }
    }

    private Signature newSignature() throws GeneralSecurityException {
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS_SHA_256);
        return signature;
    }
}

package com.zkdoc.credential;

import com.zkdoc.core.crypto.RsaPssSignatureService;
import com.zkdoc.core.crypto.SignatureService;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Component providing RSASSA-PSS digital signing for credential digests.
 * Does NOT generate random ephemeral keys at runtime; uses deterministic
 * configured keys or injected keypairs.
 */
@Component
public class CredentialRsaPssSigner {

    public static final String DEFAULT_DEV_KEY_ID = "dev-credential-signing-key-1";

    private static final String DEV_PUB_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApkgVyAQkifEKeftp4C3+2gd9MtjW0flEcxxX3/SY0fCOHQeR1xgAZ7l62gfegtMn2ZTCEWXLrB8V0Ip2ptyM8IYdcNsm7YDkivX3Js32DXoOg8fZYFrQqOsKNQ9pNbXkz3kK4yNZb1cP0UUfnUYX0QsDbk7BwfiP2Qrv85XjBJX+5H31VoC88CDiwiB6eGD++eb+GRbdqkPME9DHgUR4Act8F2i1HIdhpOHvg1W22OatZmkJEM6np77R8uDQhPRj/1nAYzQ6L9Ff4/iW+XeupP3Gxsgl1nMy0Qm9hn7CbpUxvmN9BEuH6csHU4nZxrXv5N7S447s5Twb3clGLbL4oQIDAQAB";

    private static final String DEV_PRIV_B64 =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCmSBXIBCSJ8Qp5+2ngLf7aB30y2NbR+URzHFff9JjR8I4dB5HXGABnuXraB96C0yfZlMIRZcusHxXQinam3Izwhh1w2ybtgOSK9fcmzfYNeg6Dx9lgWtCo6wo1D2k1teTPeQrjI1lvVw/RRR+dRhfRCwNuTsHB+I/ZCu/zleMElf7kffVWgLzwIOLCIHp4YP755v4ZFt2qQ8wT0MeBRHgBy3wXaLUch2Gk4e+DVbbY5q1maQkQzqenvtHy4NCE9GP/WcBjNDov0V/j+Jb5d66k/cbGyCXWczLRCb2GfsJulTG+Y30ES4fpywdTidnGte/k3tLjjuzlPBvdyUYtsvihAgMBAAECggEABCEbJV3w+Fn7jB7xuKb7wrdX5RjccNdzWzZzejTNTL1gEhl7hi9q5yiEhACZBn+u6CjZDuyLu8kD1ityGd+1Qk3aUlCm7VP7mPXVvulzeE81KBGg9NMchm1b0giBcNuFBJ/5ZL2efXTYiDZivBmfAsp2udkL18DP9bBfWBm7SwRSUZPs5kQqpjEnrfXo+QVf7HUXSGSuGps91CgCBWUOIs8MBBDJSjC07VBEQi9loEuMy5L4iV8QINg1B84tQd945DFBmH1YbgMI2btgTDoiY4GvIbSpVjwvbyDAX2Id+XdTWVSqFpCMYlmXjeJp//H5uADt8ZFXQaK7NblGPOTQAQKBgQDTPgy1vO77n9VxqefgGs52YC72o4VQZJhNcpftqJkeYt7WOiBWDbnSA3fb1hiP7Y6+YM6zNoyhVSDjgrRoxw/75VSUxhUIk2AWgoYJmZ4NLC9IB1lmLSnI2izH/K2ejRJeyg1sNtnDqoiyfuEtXlCv9cElDLgjfPtEcfHv9ar3AQKBgQDJg1OuJi6Xz0OEvQ3ADIKTozumBD4HIcCBAVsdGx12vfYxIflGIUkteW4736DAGInccP6YLYfdSGsk8H6pU5ozJT5hhxwPgA5eap0YpOn4q646QzNw816zc33LcLrTkWZh6Y8tUNgYkvHPhpDwnjHd3OzEpcvh3ttUtwgGrdahoQKBgHTE0PvJE4XORYuOMxnjV4uCAZPesjvNT49IMAl0cCem2a0r9mH7NmOL/T5tK6ir1YMROlFAbx5jU0nQH2/n0cMZctbGZCwYYz4uKm+2FfMMIJq9TUK45mcbizOFTmfdbXEuWZse+uM5KlUY49pTT2DlBT44uLUNSkv8fJN7hmUBAoGABvYadfGrumLWmkdNqkzEVgVVki6jVeu8krhRRXgiVG/LiXb6/Yk41fFT9Y2MKBaw7ISaxvcLsoFF8ki8l2zj7tBij0vzlDWASZK1Spyn2IdVxX180XIW22wp9FU4nwZu2wMOhyo7zN2GrPyhFS7h2iA/ratsl6jzOsH37QMkXwECgYA5saj4cnLyWUkoMyE/wlcD5p1AZnm7oA3jgwOEJPmLSahvTfA7QOUqWL8ZqR15NIuC8qDMxodlDQVcYyeQRdUx18uKUXPZtBCLE4V5JrLPJKTv8bl8R7w8GsarNh8tYgFLweJKz9jnIU1IokDPi7LqxxnKbTXeNy5MOHPrNlnfdA==";

    private final SignatureService signatureService;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    public CredentialRsaPssSigner(SignatureService signatureService, KeyPair keyPair, String keyId) {
        this.signatureService = Objects.requireNonNull(signatureService, "signatureService must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public CredentialRsaPssSigner(KeyPair keyPair) {
        this(new RsaPssSignatureService(), keyPair, DEFAULT_DEV_KEY_ID);
    }

    public CredentialRsaPssSigner() {
        this(new RsaPssSignatureService(), loadStableDevelopmentKeyPair(), DEFAULT_DEV_KEY_ID);
    }

    public byte[] sign(byte[] digest) {
        return signatureService.signRsaPss(digest, privateKey);
    }

    public boolean verify(byte[] digest, byte[] signature) {
        return signatureService.verifyRsaPss(digest, signature, publicKey);
    }

    public boolean verify(byte[] digest, byte[] signature, PublicKey customPublicKey) {
        return signatureService.verifyRsaPss(digest, signature, customPublicKey);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return keyId;
    }

    private static KeyPair loadStableDevelopmentKeyPair() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] pubBytes = Base64.getDecoder().decode(DEV_PUB_B64);
            byte[] privBytes = Base64.getDecoder().decode(DEV_PRIV_B64);
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse development RSA keypair", e);
        }
    }
}

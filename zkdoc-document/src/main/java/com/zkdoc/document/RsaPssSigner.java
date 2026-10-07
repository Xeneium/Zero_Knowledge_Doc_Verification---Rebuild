package com.zkdoc.document;

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
 * Component providing RSASSA-PSS digital signing for document digests.
 * Uses deterministic configured keys or injected keypairs rather than generating
 * random keys on startup.
 */
@Component
public class RsaPssSigner {

    public static final String DEFAULT_DEV_KEY_ID = "dev-document-signing-key-1";

    private static final String DEV_PUB_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuLfVQYzCM6S+ZaL+CuMmtcCPDwxOMjHHKFdqCP6Bv/quUVxfcoCT6pzfXztvUFxAXKVYlMuwtIU2HHZkknasNF8xF/BnE/7Jr6hOkCzs+6pAtZ2Fmb+ghDA6SX3fFu0pxTtPR+xfe6ESqRtfIbDIpdLJ+JX2TeONUR6XyVl+VGOfKMQZrDcyWus3mpjBBMiar6ndwiynNwJvvcyw9rjWS2Mr3mjm6PHtz2SEKNc50Bjk53f7DA1CUqJTcg72R7M9WwpIY4FgtL3S9D/O6dRNbRQNVc3oaevzjkstofz8kWV9qj8Aex7T70Mo+pVp8/+dTke+Z83I9OneDDRKaguIdwIDAQAB";

    private static final String DEV_PRIV_B64 =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC4t9VBjMIzpL5lov4K4ya1wI8PDE4yMccoV2oI/oG/+q5RXF9ygJPqnN9fO29QXEBcpViUy7C0hTYcdmSSdqw0XzEX8GcT/smvqE6QLOz7qkC1nYWZv6CEMDpJfd8W7SnFO09H7F97oRKpG18hsMil0sn4lfZN441RHpfJWX5UY58oxBmsNzJa6zeamMEEyJqvqd3CLKc3Am+9zLD2uNZLYyveaObo8e3PZIQo1znQGOTnd/sMDUJSolNyDvZHsz1bCkhjgWC0vdL0P87p1E1tFA1Vzehp6/OOSy2h/PyRZX2qPwB7HtPvQyj6lWnz/51OR75nzcj06d4MNEpqC4h3AgMBAAECggEAArM9PM4P8K1R0y6ExCf54uJDtRaa9gTgQmi0thjyUJobfjj5dDUI9H8CjOPThrsyfag1GUe494qS9euNIM73SXSyxQTQnOw2iojNTTI67Jrag00gLNUuRYBm+bgLUMx6tdVeHVEJPEdofGxYAagR5sqkBSw8A3t2D1+BHIJgZF9iDA5HAnQTrt8arMoTK6KAxqfLU8ITLJqxFpXreyzrsW/tTyS/CIneUloWJQ47Ump7twXzj5Xje0iYaxCzJ3Uu1WTjXnq6lvlr836DWIPuww6x596/qxmO0b8wn1PKoPPr/zwSquQEXPvcsKQYuykSs3zpS0YiJ8cByL7CNOI9kQKBgQDGseOZz6rSbsI5nro+gTFWkEB+1cWlH3d8spXf91rh8SGEPft54fOx/+qRIORWNzGal0RDn3bRg9sbpTU6UeaVfAT8BvqCkmQZV3f72F1gPHdya7QhEQ2yUkdI98rihn2uxempuBUQyK44adqjcO6EgGxtTl3pHQQ8be/bOgvGYwKBgQDt/gFZuHM4HrjhJvWAdC3ezX0mZEc6BwK/ZUQo43fpCJZusHIwSqB5v1V5aAxxDXcFgGQx76yaLmxxE6IX18fEk7zxrKrspMCgP6mrx16I+9i9gKPKD1JAWfEWw70R07XZzmcAdVxEOCiIv4z7FheMGD+VdYPNKVtTp74Iq1Y33QKBgHkdPesn1qxmryWIR2aKQZTOYUHu9IbuY1szUIAwusk2voNpdLyaqn05zbWhIw4anrXotsexkC+t/AUh8wwVmEROUIt/hXvJ2BJKauysul5K/VRVqnrtdjfYR3OG97iU2k+KfB/kAZM1gF0KyScPU5SY437NYhkSgt6C9M3g2ASZAoGBAOORACWUc8fzyICySgncDYiAIO028cyQq5Xr9PLMHvqbFY0d9vIqSaxgwhBm546qe2atUCFXtklvno25YkM+N2zGf+LvYjkJiI8ykMEK0GBXwPXjLbqmT9XsqtVDsWR8deSgsmkDz4gD0u5/iwUEKNvyy8nUJoVkyEkDmnibrMOZAoGADpwoMtlQY0JxC9bUJa377BeS3YDXrRf3M7foEtUTH8VgNM2pdHdM0QIOlrt99nlpYqE2elEBv2gJpu88Kj12Gt3iIjuCztsrjv1fVc2Yu1+8O1xkvr/sBGhp9/bG/4h8usCU9mFoksXJxBem7vXmCCenDMKfJu87wIUOVdgyIKE=";

    private final SignatureService signatureService;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    public RsaPssSigner(SignatureService signatureService, KeyPair keyPair, String keyId) {
        this.signatureService = Objects.requireNonNull(signatureService, "signatureService must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public RsaPssSigner(KeyPair keyPair) {
        this(new RsaPssSignatureService(), keyPair, DEFAULT_DEV_KEY_ID);
    }

    public RsaPssSigner() {
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
            throw new IllegalStateException("Failed to parse development document RSA keypair", e);
        }
    }
}

package com.zkdoc.api.demo;

import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.document.RsaPssSigner;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Cryptographic constants, deterministic demo keypairs, and synthetic identifiers
 * for the Zero-Knowledge Document Verification Platform demo environment.
 * <p>
 * Strictly for local development, integration testing, and faculty/evaluator demonstrations.
 * All keys and certificates here are synthetic and explicitly non-production.
 */
public final class DemoCryptoKeyConstants {

    private DemoCryptoKeyConstants() {}

    // =========================================================================
    // 1. JWT Signing Key & Claims Constants
    // =========================================================================

    public static final String DEMO_JWT_KEY_ID = "demo-jwt-key-1";
    public static final String DEMO_JWT_ISSUER = "https://auth.zkdoc.internal";
    public static final String DEMO_JWT_AUDIENCE = "zkdoc-api";

    /** Deterministic RSA-2048 private key (PKCS#8 Base64) for demo JWT signing */
    public static final String DEMO_JWT_PRIV_B64 =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDPZFReGnvHMNIaWZHPD/ws" +
            "DpGryW4jUDbeOiI1j6/cku9kFvtfnGY5x/AuW6Meqz3LTJfiFyzHp6eUvOpndi9jHiA8BbHw" +
            "fy6t+cQ3PuG/DeSv803jG1I20R9DqdMVM7gOmJ1mv6lAuFmSDUf2Cmd1AMBCHJL0r1A+Vc9z" +
            "9T/NA3/BlZbohEapYuKytjPduZgmlstMD4qvm/MZMHm61LtUqTMIliMvcdqRgxXNqC7H2C/L" +
            "pW9tnY+HYz8je93IYbtbHiWDiRMBbXLcLsWf9njQoo+XXc+elE6rZbIHmPdime+OfNO3cklm" +
            "p0lUBTqk8eddstfIzmYWo2HYd8dIAr83AgMBAAECgf9iqW+JB4x2RhESxZk7D0Cx8vaQTqCL" +
            "M/NDaDYE2H1FsAYT3XY05wQX7Uvw//Tf4EmsRuqlR1ljZBkXLUSUYGcTgjHymhhSlxdnnTSc" +
            "eHxRhs5jkOeUE8CkFVST+wgLcvQkt6w5a59AG1wzqsh57vVI2wrZvhOQNQ0mLeqLGPK9VoU+" +
            "LFF3RkRis97qj24tLojgV+kGFYQcLw8PatKPj2assE32JL3d+VR9qmmG/mA/sxCl1wUp047m" +
            "A5symo5lUrurUZJqjLOvXFa80DPXG5l7X/GgXw3cD7MgTWDZPA6gpdP6Fj81Iy3oMU0X4rZ0" +
            "KIOi+2BxvBSMFcKWUyrHK0ECgYEA+W3IvytnSR6u2qH1loEi39kUYmSnWeUgnFqX3YBUWi/u" +
            "ImOgmINzd2I/dJIeY7vkyIWczpYQXDlOBZBI/BoxHO2+SuMnxxnPVgy5aujFmTWB5Xt/RDvb" +
            "ho6LfdQNtSKLg34WCE2qJ9je2+aTmUCsqbt9wVmx5oNnm4Y+mhNmF9cCgYEA1NsJcyekt639" +
            "097MoCT31CTO6cGlere/pkyYmhvi/+TH6YFNXg+0ZwxuQYkYvg1ieA90JZScDcrPTpiNyRge" +
            "ERk/sIGtW5c6L1S2UwkneiuVayxXabbANIOHStC8HzAdhCqRo23g7JdyklgomFWCVIzIAm3L" +
            "U/yftPvcsdy0J6ECgYEAxxhynOWwVBVBzRJqTfBWPgew1bkyO/dAyI0nm47LxhFsK9cKYbLL" +
            "gAfUTuAzkbhxLf445E20UWkMdjRtjY36lfTPaEdZgPuBHFabylJzJ5AmV170fyqoWAeAILgb" +
            "GPKyGguS2p3jrFrXyP+L4VBWgkU+ZA+MiUtqcCH/5kyFuZECgYBCOjCTgscuW1V5oX3bagUu" +
            "HZG/i3/YUemwbW6hrIGRivZkgdW3qX1A/s4jV+u53mI/5tcFhlqTtAGSe8bJxdY9DbUSTxbI" +
            "Q6JX7ayY42VEDuF7qh08iB7/d9kVfyK5NXhJkhEkX2C3XFvKiuF+nERj2DW3A8BazmRGpILD" +
            "WF85YQKBgQDwGE63n+DZykHuM0ZKCTu6VSACkctVwifdd6zsk4QhEqB//0knfSzbqpnXLxmL" +
            "cznXENStrwt5otDeco49PAKl2kD57jKCj8Uefmd37qq4XUdhL36rJyA+aQEuL273XS64528K" +
            "Q7My9hQPotA+y+61bzOUW4g4HgP2OisU2/h+ww==";

    /** Deterministic RSA-2048 public key (X.509 Base64) for demo JWT verification */
    public static final String DEMO_JWT_PUB_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy2RUXhp7xzDSGlmRzw/8LA6Rq8lu" +
            "I1A23joiNY+v3JLvZBb7X5xmOcfwLlujHqs9y0yX4hcsx6enlLzqZ3YvYx4gPAWx8H8urfnE" +
            "Nz7hvw3kr/NN4xtSNtEfQ6nTFTO4DpidZr+pQLhZkg1H9gpndQDAQhyS9K9QPlXPc/U/zQN/" +
            "wZWW6IRGqWLisrYz3bmYJpbLTA+Kr5vzGTB5utS7VKkzCJYjL3HakYMVzagux9gvy6VvbZ2P" +
            "h2M/I3vdyGG7Wx4lg4kTAW1y3C7Fn/Z40KKPl13PnpROq2WyB5j3YpnvjnzTt3JJZqdJVAU6" +
            "pPHnXbLXyM5mFqNh2HfHSAK/NwIDAQAB";

    public static final String DEMO_JWT_PUB_PEM =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy2RUXhp7xzDSGlmRzw/8\n" +
            "LA6Rq8luI1A23joiNY+v3JLvZBb7X5xmOcfwLlujHqs9y0yX4hcsx6enlLzqZ3Yv\n" +
            "Yx4gPAWx8H8urfnENz7hvw3kr/NN4xtSNtEfQ6nTFTO4DpidZr+pQLhZkg1H9gpn\n" +
            "dQDAQhyS9K9QPlXPc/U/zQN/wZWW6IRGqWLisrYz3bmYJpbLTA+Kr5vzGTB5utS7\n" +
            "VKkzCJYjL3HakYMVzagux9gvy6VvbZ2Ph2M/I3vdyGG7Wx4lg4kTAW1y3C7Fn/Z4\n" +
            "0KKPl13PnpROq2WyB5j3YpnvjnzTt3JJZqdJVAU6pPHnXbLXyM5mFqNh2HfHSAK/\n" +
            "NwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    // =========================================================================
    // 2. Platform Key IDs
    // =========================================================================

    public static final String CREDENTIAL_SIGNING_KEY_ID = CredentialRsaPssSigner.DEFAULT_DEV_KEY_ID;
    public static final String DOCUMENT_SIGNING_KEY_ID = RsaPssSigner.DEFAULT_DEV_KEY_ID;

    // =========================================================================
    // 3. Synthetic Demo Personas & Identity URNs
    // =========================================================================

    public static final String ADMIN_SUBJECT = "urn:zkdoc:admin:operator-root";
    public static final String ISSUER_SUBJECT = "urn:zkdoc:issuer:acme-university";
    public static final String ISSUER_2_SUBJECT = "urn:zkdoc:issuer:apex-cert";
    public static final String HOLDER_SUBJECT = "urn:zkdoc:holder:alice-student";
    public static final String VERIFIER_SUBJECT = "urn:zkdoc:verifier:regulator-agent";

    public static final String ISSUER_NAME = "Acme State University - Office of the Registrar";
    public static final String ISSUER_2_NAME = "Apex Global Certification Authority";

    // =========================================================================
    // 4. Deterministic Pre-Seeded UUIDs & Tokens
    // =========================================================================

    /** Sample Bachelor Degree diploma document */
    public static final UUID DEMO_DOC_1_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    public static final String DEMO_DOC_1_TOKEN = "tok_demo_degree_diploma_2024";

    /** Sample Cybersecurity Training Certificate document */
    public static final UUID DEMO_DOC_2_ID = UUID.fromString("d0000000-0000-4000-8000-000000000002");
    public static final String DEMO_DOC_2_TOKEN = "tok_demo_training_cert_2024";

    /** Credential 1: Bachelor of Science in Computer Science (Active) */
    public static final UUID DEMO_CRED_1_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    public static final String DEMO_CRED_1_TOKEN = "tok_demo_alice_degree_cred";

    /** Credential 2: Certified Cloud Security Architect (Active) */
    public static final UUID DEMO_CRED_2_ID = UUID.fromString("a0000000-0000-4000-8000-000000000002");

    /** Credential 3: Special Research Laboratory Permit (Suspended) */
    public static final UUID DEMO_CRED_3_ID = UUID.fromString("a0000000-0000-4000-8000-000000000003");

    /** Credential 4: Equipment & Facility Pass (Revoked) */
    public static final UUID DEMO_CRED_4_ID = UUID.fromString("a0000000-0000-4000-8000-000000000004");

    // =========================================================================
    // Key Parsing Utilities
    // =========================================================================

    public static KeyPair loadDemoJwtKeyPair() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] privBytes = Base64.getDecoder().decode(DEMO_JWT_PRIV_B64);
            PrivateKey priv = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(privBytes));
            PublicKey pub;
            if (priv instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
                pub = kf.generatePublic(new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            } else {
                byte[] pubBytes = Base64.getDecoder().decode(DEMO_JWT_PUB_B64);
                pub = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubBytes));
            }
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load demo JWT keypair", e);
        }
    }
}

package com.zkdoc.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Test helper for generating valid, tampered, expired, and malicious JWT tokens
 * for adversarial testing of the Phase 12 security architecture.
 */
public final class JwtTokenTestHelper {

    private JwtTokenTestHelper() {}

    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not supported", e);
        }
    }

    public static String createSignedJwt(
            KeyPair keyPair,
            String keyId,
            String issuer,
            String audience,
            String subject,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt,
            Instant notBefore) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject)
                    .issueTime(issuedAt != null ? Date.from(issuedAt) : new Date())
                    .expirationTime(expiresAt != null ? Date.from(expiresAt) : Date.from(Instant.now().plusSeconds(3600)));

            if (notBefore != null) {
                claimsBuilder.notBeforeTime(Date.from(notBefore));
            }
            if (roles != null && !roles.isEmpty()) {
                claimsBuilder.claim("roles", roles);
            }

            SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
            signedJWT.sign(new RSASSASigner(keyPair.getPrivate()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test signed JWT", e);
        }
    }

    public static String createCustomJwt(
            KeyPair signingKey,
            JWSAlgorithm algorithm,
            String keyId,
            Map<String, Object> customHeaders,
            JWTClaimsSet claims) {
        try {
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(algorithm);
            if (keyId != null) {
                headerBuilder.keyID(keyId);
            }
            if (customHeaders != null) {
                customHeaders.forEach(headerBuilder::customParam);
            }

            SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claims);
            if (signingKey != null) {
                signedJWT.sign(new RSASSASigner(signingKey.getPrivate()));
            }
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate custom test JWT", e);
        }
    }
}

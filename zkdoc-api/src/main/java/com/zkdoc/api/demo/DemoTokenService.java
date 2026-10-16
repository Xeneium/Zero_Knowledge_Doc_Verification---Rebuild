package com.zkdoc.api.demo;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Service generating RFC 7519 / RFC 8725 compliant signed Bearer JWT tokens
 * for the demo accounts using the deterministic demo JWT signing key.
 */
@Service
public class DemoTokenService {

    private final KeyPair keyPair;

    public DemoTokenService() {
        this.keyPair = DemoCryptoKeyConstants.loadDemoJwtKeyPair();
    }

    /**
     * Generates a signed JWT for the given subject and roles with specified duration.
     */
    public String createToken(String subject, List<String> roles, Duration validity) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plus(validity);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(DemoCryptoKeyConstants.DEMO_JWT_KEY_ID)
                    .build();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(DemoCryptoKeyConstants.DEMO_JWT_ISSUER)
                    .audience(DemoCryptoKeyConstants.DEMO_JWT_AUDIENCE)
                    .subject(subject)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim("roles", roles)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(keyPair.getPrivate()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint demo token for " + subject, e);
        }
    }

    /**
     * Generates standard 30-day token for Admin role.
     */
    public String getAdminToken() {
        return createToken(DemoCryptoKeyConstants.ADMIN_SUBJECT, List.of("ADMIN"), Duration.ofDays(30));
    }

    /**
     * Generates standard 30-day token for Issuer role.
     */
    public String getIssuerToken() {
        return createToken(DemoCryptoKeyConstants.ISSUER_SUBJECT, List.of("ISSUER"), Duration.ofDays(30));
    }

    /**
     * Generates standard 30-day token for Holder role.
     */
    public String getHolderToken() {
        return createToken(DemoCryptoKeyConstants.HOLDER_SUBJECT, List.of("HOLDER"), Duration.ofDays(30));
    }

    /**
     * Generates standard 30-day token for Verifier role.
     */
    public String getVerifierToken() {
        return createToken(DemoCryptoKeyConstants.VERIFIER_SUBJECT, List.of("VERIFIER"), Duration.ofDays(30));
    }

    /**
     * Summary record for demo account metadata and pre-minted Bearer JWT.
     */
    public record DemoAccountDetails(
            String id,
            String name,
            String role,
            String subject,
            String description,
            String token) {}

    public List<DemoAccountDetails> getAllDemoAccounts() {
        return List.of(
                new DemoAccountDetails(
                        "admin",
                        "Platform Administrator",
                        "ADMIN",
                        DemoCryptoKeyConstants.ADMIN_SUBJECT,
                        "Full administrative control: key trust management, audit log inspection, disaster recovery validation.",
                        getAdminToken()
                ),
                new DemoAccountDetails(
                        "issuer",
                        "Acme University Registrar",
                        "ISSUER",
                        DemoCryptoKeyConstants.ISSUER_SUBJECT,
                        "Academic issuing authority: document uploads, verifiable credential issuance, and lifecycle management.",
                        getIssuerToken()
                ),
                new DemoAccountDetails(
                        "holder",
                        "Alice Chen (Student / Credential Holder)",
                        "HOLDER",
                        DemoCryptoKeyConstants.HOLDER_SUBJECT,
                        "Graduate student holder: personal wallet, attribute commitment inspection, Bulletproof zero-knowledge predicate proof generation.",
                        getHolderToken()
                ),
                new DemoAccountDetails(
                        "verifier",
                        "National Accreditation & Compliance Verifier",
                        "VERIFIER",
                        DemoCryptoKeyConstants.VERIFIER_SUBJECT,
                        "Third-party verifier: verifies document hash integrity, RSA-PSS signatures, QR capabilities, and zero-knowledge predicate proofs.",
                        getVerifierToken()
                )
        );
    }
}

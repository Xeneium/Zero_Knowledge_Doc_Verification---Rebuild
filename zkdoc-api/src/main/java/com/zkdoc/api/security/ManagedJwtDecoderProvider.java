package com.zkdoc.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.SignedJWT;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.RegisteredKey;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Managed, cached JWT decoder provider adhering to RFC 8725 JWT Best Current Practices.
 * Enforces algorithm restrictions, key trust domain isolation, exact issuer & audience
 * verification, semantic claim validation, and lifecycle caching so decoders and public
 * keys are not parsed or constructed per request.
 */
@Component
public class ManagedJwtDecoderProvider {

    private static final Logger log = LoggerFactory.getLogger(ManagedJwtDecoderProvider.class);

    private final KeyRegistryService keyRegistryService;
    private final JwtSecurityProperties properties;
    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();
    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public ManagedJwtDecoderProvider(
            KeyRegistryService keyRegistryService,
            JwtSecurityProperties properties) {
        this.keyRegistryService = Objects.requireNonNull(keyRegistryService, "keyRegistryService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Authenticates and verifies a JWT token string against the trusted key registry,
     * algorithm constraints, and semantic claim policies.
     *
     * @param token Compact serialized JWT string
     * @return Fully validated Spring Security {@link Jwt} instance
     * @throws JwtException if signature, key trust, or claim validation fails
     */
    public Jwt decode(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Bearer token must not be empty");
        }

        SignedJWT parsedJwt;
        try {
            parsedJwt = SignedJWT.parse(token);
        } catch (Exception e) {
            log.warn("Failed to parse JWT structure: {}", e.getMessage());
            throw new JwtException("Malformed JWT token structure", e);
        }

        JWSHeader header = parsedJwt.getHeader();
        validateHeader(header);

        String keyId = header.getKeyID();
        JwtDecoder decoder = getOrCreateDecoder(keyId, header.getAlgorithm().getName());
        return decoder.decode(token);
    }

    /**
     * Validates JWS header constraints per RFC 8725.
     */
    private void validateHeader(JWSHeader header) {
        if (header == null) {
            throw new JwtException("Missing JWT header");
        }

        JWSAlgorithm algorithm = header.getAlgorithm();
        if (algorithm == null || "none".equalsIgnoreCase(algorithm.getName())) {
            throw new JwtException("Rejected unsecure JWT algorithm: " + (algorithm != null ? algorithm.getName() : "null"));
        }

        String algName = algorithm.getName().toUpperCase();

        // Reject symmetric algorithms explicitly (protect against HMAC / key-confusion attacks)
        if (algName.startsWith("HS")) {
            throw new JwtException("Algorithm confusion attempt: Symmetric algorithm " + algName + " is forbidden");
        }

        // Enforce allowed algorithms allowlist
        Set<String> allowed = properties.getAllowedAlgorithms();
        if (!allowed.contains(algName)) {
            throw new JwtException("Unsupported signing algorithm " + algName + ", expected one of: " + allowed);
        }

        // Header must contain keyId
        String keyId = header.getKeyID();
        if (keyId == null || keyId.isBlank() || keyId.length() > 128) {
            throw new JwtException("Missing or invalid key ID ('kid') in JWT header");
        }

        // Protect against JWK / JKU header injection (RFC 8725 §3.8)
        if (header.getJWK() != null || header.getJWKURL() != null) {
            throw new JwtException("Untrusted public key embedded in JWT header ('jwk'/'jku' forbidden)");
        }
    }

    /**
     * Retrieves or builds a cached NimbusJwtDecoder configured with comprehensive validators.
     */
    private JwtDecoder getOrCreateDecoder(String keyId, String algorithmName) {
        return decoderCache.computeIfAbsent(keyId, kid -> {
            RegisteredKey regKey = keyRegistryService.verificationKey(kid, KeyPurpose.JWT_SIGNING)
                    .orElseThrow(() -> new JwtException("JWT signing key is unknown, revoked, or not authorized for JWT_SIGNING: " + kid));

            if (!regKey.status().canVerifyHistorical()) {
                throw new JwtException("JWT signing key is not valid for verification: " + kid);
            }

            RSAPublicKey publicKey = keyCache.computeIfAbsent(kid, k -> parseRsaPublicKey(regKey.publicKeyPem()));

            var builder = NimbusJwtDecoder.withPublicKey(publicKey);
            if ("PS256".equalsIgnoreCase(algorithmName)) {
                builder.signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.PS256);
            } else {
                builder.signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
            }

            NimbusJwtDecoder decoder = builder.build();

            // Configure composite OAuth2TokenValidator chain
            Duration clockSkew = Duration.ofSeconds(properties.getClockSkewSeconds());
            OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(clockSkew);
            OAuth2TokenValidator<Jwt> issuerValidator = new ExactIssuerValidator(properties.getIssuer(), properties.isStrictIssuer());
            OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(properties.getAudience(), properties.isStrictAudience());
            OAuth2TokenValidator<Jwt> semanticValidator = new SemanticClaimsValidator();

            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    timestampValidator, issuerValidator, audienceValidator, semanticValidator));

            return decoder;
        });
    }

    /**
     * Parses an RSA public key from X.509 PEM string.
     */
    private RSAPublicKey parseRsaPublicKey(String pem) {
        try {
            String encoded = pem.replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA public key PEM from key registry", e);
        }
    }

    /**
     * Evicts a key from decoder and key cache when key status changes or is rotated.
     */
    public void evict(String keyId) {
        if (keyId != null) {
            decoderCache.remove(keyId);
            keyCache.remove(keyId);
        }
    }

    /**
     * Clears all cached decoders and keys.
     */
    public void clearCache() {
        decoderCache.clear();
        keyCache.clear();
    }

    public int getCacheSize() {
        return decoderCache.size();
    }

    // --- Custom JWT Validators ---

    private static class ExactIssuerValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedIssuer;
        private final boolean strict;

        ExactIssuerValidator(String expectedIssuer, boolean strict) {
            this.expectedIssuer = expectedIssuer;
            this.strict = strict;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String issuer = jwt.getClaimAsString("iss");
            if (issuer == null || issuer.isBlank()) {
                if (strict) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing required 'iss' claim", null));
                }
                return OAuth2TokenValidatorResult.success();
            }
            if (!expectedIssuer.equals(issuer)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Untrusted JWT issuer: " + issuer + ", expected: " + expectedIssuer, null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }

    private static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedAudience;
        private final boolean strict;

        AudienceValidator(String expectedAudience, boolean strict) {
            this.expectedAudience = expectedAudience;
            this.strict = strict;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audiences = jwt.getAudience();
            if (audiences == null || audiences.isEmpty()) {
                if (strict) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing required 'aud' claim", null));
                }
                return OAuth2TokenValidatorResult.success();
            }
            if (!audiences.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "JWT audience mismatch: " + audiences + ", expected to include: " + expectedAudience, null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }

    private static class SemanticClaimsValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Missing or blank subject ('sub') claim", null));
            }

            Instant iat = jwt.getIssuedAt();
            Instant exp = jwt.getExpiresAt();
            if (iat != null && exp != null && exp.isBefore(iat)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Malformed token timeframe: exp (" + exp + ") is before iat (" + iat + ")", null));
            }

            return OAuth2TokenValidatorResult.success();
        }
    }
}

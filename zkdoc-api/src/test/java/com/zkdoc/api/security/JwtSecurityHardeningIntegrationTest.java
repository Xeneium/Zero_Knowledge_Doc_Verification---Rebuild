package com.zkdoc.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.zkdoc.api.Application;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtSecurityHardeningIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeyRegistry keyRegistry;

    @Autowired
    private ManagedJwtDecoderProvider managedJwtDecoderProvider;

    private KeyPair validRsaKeyPair;
    private String validKeyId;
    private final String expectedIssuer = "https://auth.zkdoc.example.com";
    private final String expectedAudience = "zkdoc-platform-api";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
        registry.add("zkdoc.security.jwt.issuer", () -> "https://auth.zkdoc.example.com");
        registry.add("zkdoc.security.jwt.audience", () -> "zkdoc-platform-api");
        registry.add("zkdoc.security.jwt.clock-skew-seconds", () -> "30");
    }

    @BeforeAll
    void setupKeys() {
        validRsaKeyPair = JwtTokenTestHelper.generateRsaKeyPair();
        validKeyId = "jwt-key-" + UUID.randomUUID();

        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(validRsaKeyPair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        RegisteredKey regKey = new RegisteredKey(
                UUID.randomUUID(),
                validKeyId,
                KeyPurpose.JWT_SIGNING,
                KeyStatus.ACTIVE,
                "RSA",
                pem,
                "test"
        );

        keyRegistry.register(regKey);
    }

    @Test
    @DisplayName("valid signed JWT with matching iss and aud authenticates successfully")
    void validJwtAuthenticates() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rejects JWT with untrusted / mismatched issuer")
    void rejectUntrustedIssuer() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                "https://malicious-issuer.attacker.com",
                expectedAudience,
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects JWT with untrusted / mismatched audience")
    void rejectUntrustedAudience() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                "foreign-client-app",
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects expired JWT beyond clock skew")
    void rejectExpiredJwt() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600), // expired 1 hour ago
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects future token where not-before nbf is in the future")
    void rejectNotBeforeFutureJwt() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(1800) // valid in 30 minutes
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects JWT with blank or empty subject")
    void rejectBlankSubject() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "   ",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects unsigned plain JWT (alg=none)")
    void rejectAlgNone() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(expectedIssuer)
                .audience(expectedAudience)
                .subject("attacker")
                .claim("roles", List.of("ADMIN"))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();

        PlainJWT plain = new PlainJWT(claims);
        String token = plain.serialize();

        mockMvc.perform(get("/api/admin/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects symmetric HMAC algorithm (HS256) signature")
    void rejectSymmetricAlgorithm() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(expectedIssuer)
                .audience(expectedAudience)
                .subject("attacker")
                .claim("roles", List.of("ADMIN"))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();

        // Construct token with HS256 header and bogus signature
        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"some-key\"}".getBytes());
        String claimsB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().getBytes());
        String token = headerB64 + "." + claimsB64 + ".AAAA";

        mockMvc.perform(get("/api/admin/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects JWT with embedded jwk or jku header")
    void rejectEmbeddedJwkOrJkuHeader() throws Exception {
        // Construct token manually with embedded "jwk" or "jku" header parameter
        String headerJson = String.format("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"%s\",\"jwk\":{\"kty\":\"RSA\"}}", validKeyId);
        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(expectedIssuer)
                .audience(expectedAudience)
                .subject("attacker")
                .claim("roles", List.of("ADMIN"))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        String claimsB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().getBytes());

        // Sign the signing input with valid key pair
        byte[] signingInput = (headerB64 + "." + claimsB64).getBytes();
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(validRsaKeyPair.getPrivate());
        sig.update(signingInput);
        byte[] sigBytes = sig.sign();
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

        String token = headerB64 + "." + claimsB64 + "." + sigB64;

        mockMvc.perform(get("/api/admin/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects JWT signed by foreign/unregistered key ID")
    void rejectUnregisteredKeyId() throws Exception {
        KeyPair foreignKeyPair = JwtTokenTestHelper.generateRsaKeyPair();
        String token = JwtTokenTestHelper.createSignedJwt(
                foreignKeyPair,
                "unregistered-key-" + UUID.randomUUID(),
                expectedIssuer,
                expectedAudience,
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects JWT with tampered signature")
    void rejectTamperedSignature() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "holder-user-1",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        // Tamper with signature segment
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 2) + "==";

        mockMvc.perform(get("/api/holder/credentials")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects unauthorized role escalation (e.g. HOLDER attempting to access ADMIN endpoint)")
    void rejectRoleEscalation() throws Exception {
        String token = JwtTokenTestHelper.createSignedJwt(
                validRsaKeyPair,
                validKeyId,
                expectedIssuer,
                expectedAudience,
                "regular-holder",
                List.of("HOLDER"),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null
        );

        mockMvc.perform(get("/api/admin/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-jwt-it-").resolve("zkdoc-jwt.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}

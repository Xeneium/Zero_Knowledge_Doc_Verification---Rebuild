package com.zkdoc.keys;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative cryptographic trust metadata record.
 * Represents public-key trust anchors, lifecycle states, and parameters.
 * Private-key material is NEVER stored in this record or the trust registry.
 */
public record RegisteredKey(
        UUID id,
        String keyId,
        KeyPurpose purpose,
        KeyStatus status,
        String algorithm,
        String algorithmParameters,
        String ownerId,
        KeyOwnerType ownerType,
        String publicKeyPem,
        byte[] publicKeyDer,
        String publicKeyEncoding,
        String fingerprint,
        Instant createdAt,
        Instant activatedAt,
        Instant retiredAt,
        Instant revokedAt,
        String revocationReason,
        String replacementKeyId,
        int version,
        Map<String, String> metadata) {

    public RegisteredKey {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(ownerType, "ownerType must not be null");
        Objects.requireNonNull(publicKeyPem, "publicKeyPem must not be null");

        if (algorithmParameters == null) {
            algorithmParameters = KeyAlgorithmPolicy.MANDATED_PARAMETERS;
        }
        if (publicKeyEncoding == null) {
            publicKeyEncoding = "X.509/PEM";
        }
        if (publicKeyDer == null) {
            publicKeyDer = decodePemToDer(publicKeyPem);
        } else {
            publicKeyDer = publicKeyDer.clone();
        }
        if (fingerprint == null) {
            fingerprint = KeyFingerprint.fromDer(publicKeyDer).value();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == KeyStatus.ACTIVE && activatedAt == null) {
            activatedAt = createdAt;
        }
        metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }

    /**
     * Backwards-compatible constructor for early Phase 1 callers.
     */
    public RegisteredKey(
            UUID id,
            String keyId,
            KeyPurpose purpose,
            KeyStatus status,
            String algorithm,
            String publicKeyPem,
            String privateKeyFileEnvironment) {
        this(
                id != null ? id : UUID.randomUUID(),
                keyId,
                purpose,
                status,
                algorithm,
                KeyAlgorithmPolicy.MANDATED_PARAMETERS,
                "platform",
                KeyOwnerType.PLATFORM,
                publicKeyPem,
                decodePemToDer(publicKeyPem),
                "X.509/PEM",
                KeyFingerprint.fromDer(decodePemToDer(publicKeyPem)).value(),
                Instant.now(),
                status == KeyStatus.ACTIVE ? Instant.now() : null,
                status == KeyStatus.RETIRED ? Instant.now() : null,
                status == KeyStatus.REVOKED ? Instant.now() : null,
                null,
                null,
                1,
                privateKeyFileEnvironment != null
                        ? Map.of("environment", privateKeyFileEnvironment)
                        : Collections.emptyMap());
    }

    /**
     * Factory for registering a new active key.
     */
    public static RegisteredKey createActive(
            String keyId,
            KeyPurpose purpose,
            String ownerId,
            KeyOwnerType ownerType,
            PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        KeyAlgorithmPolicy.validatePublicKey(publicKey);

        byte[] der = publicKey.getEncoded();
        String pem = encodeDerToPem(der);
        String fingerprint = KeyFingerprint.fromDer(der).value();
        Instant now = Instant.now();

        return new RegisteredKey(
                UUID.randomUUID(),
                keyId,
                purpose,
                KeyStatus.ACTIVE,
                KeyAlgorithmPolicy.MANDATED_ALGORITHM,
                KeyAlgorithmPolicy.MANDATED_PARAMETERS,
                ownerId,
                ownerType,
                pem,
                der,
                "X.509/PEM",
                fingerprint,
                now,
                now,
                null,
                null,
                null,
                null,
                1,
                Collections.emptyMap());
    }

    private static final Map<String, PublicKey> PARSED_KEY_CACHE = new ConcurrentHashMap<>();

    /**
     * Converts the registered public key bytes into a parsed Java PublicKey instance.
     * Caches the immutable PublicKey in memory to avoid repeated ASN.1 decoding and KeyFactory parsing.
     */
    public PublicKey toPublicKey() {
        String cacheKey = (fingerprint != null && !fingerprint.isBlank()) ? fingerprint : keyId;
        return PARSED_KEY_CACHE.computeIfAbsent(cacheKey, k -> {
            try {
                X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyDer());
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IllegalStateException("Failed to parse public key for keyId: " + keyId, e);
            }
        });
    }

    @Override
    public byte[] publicKeyDer() {
        return publicKeyDer != null ? publicKeyDer.clone() : decodePemToDer(publicKeyPem);
    }

    public static byte[] decodePemToDer(String pem) {
        Objects.requireNonNull(pem, "pem must not be null");
        String sanitized = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(sanitized);
    }

    public static String encodeDerToPem(byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }
}

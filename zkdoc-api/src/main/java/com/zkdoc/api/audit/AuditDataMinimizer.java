package com.zkdoc.api.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Enforces data minimization, secret exclusion, and log injection defense
 * across all audit event fields before persistence.
 */
public final class AuditDataMinimizer {

    private static final int MAX_METADATA_LENGTH = 1024;
    private static final int MAX_STRING_FIELD_LENGTH = 128;
    private static final String IP_SALT = "zkdoc-audit-salt-v1";

    // Prohibited secret patterns
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9._~+/-]+");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("(?i)-----BEGIN[ A-Z0-9_-]*PRIVATE KEY-----");

    private AuditDataMinimizer() {}

    /**
     * Sanitizes string values by removing CRLF and control characters to prevent log injection.
     */
    public static String sanitizeString(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        // Strip control characters (including \r and \n)
        String sanitized = input.replaceAll("[\\r\\n\\t\\u0000-\\u001F\\u007F]", " ").trim();
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }

    /**
     * Hashes an IP address using SHA-256 with a salt, preventing raw IP address retention
     * while preserving pseudonymized correlation capability.
     */
    public static String hashClientIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank() || "unknown".equalsIgnoreCase(rawIp)) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(IP_SALT.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(rawIp.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Validates and cleans metadata JSON string, ensuring bounded size and strictly
     * prohibiting raw JWTs, bearer tokens, and private key headers.
     */
    public static String sanitizeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        // Assert absence of secrets
        if (JWT_PATTERN.matcher(metadataJson).find() ||
            BEARER_PATTERN.matcher(metadataJson).find() ||
            PRIVATE_KEY_PATTERN.matcher(metadataJson).find()) {
            throw new SecurityException("Prohibited secret pattern detected in audit metadata");
        }

        String sanitized = sanitizeString(metadataJson, MAX_METADATA_LENGTH);
        return sanitized;
    }

    /**
     * Asserts that a value does not contain prohibited secret material.
     */
    public static void assertNoSecretMaterial(String value, String fieldName) {
        if (value == null) {
            return;
        }
        if (JWT_PATTERN.matcher(value).find() ||
            BEARER_PATTERN.matcher(value).find() ||
            PRIVATE_KEY_PATTERN.matcher(value).find()) {
            throw new SecurityException("Prohibited secret material detected in audit field: " + fieldName);
        }
    }
}

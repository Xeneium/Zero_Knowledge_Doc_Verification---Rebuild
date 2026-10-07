package com.zkdoc.api.qr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * Strongly typed, canonical QR payload representation.
 * Encodes only protocol version, verification mode, opaque capability token, and verification URI.
 * Strictest privacy invariant: NEVER embeds private keys, proving material, raw attributes,
 * or document content bytes.
 */
public record QrVerificationPayload(
        @JsonProperty("v") String version,
        @JsonProperty("m") VerificationMode mode,
        @JsonProperty("tok") String capabilityToken,
        @JsonProperty("uri") String verificationUri
) {
    public static final String PROTOCOL_VERSION_V1 = "QR_V1";
    private static final Set<String> ALLOWED_VERSIONS = Set.of(PROTOCOL_VERSION_V1);
    public static final int MAX_PAYLOAD_BYTES = 2048;

    public QrVerificationPayload {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(capabilityToken, "capabilityToken must not be null");
        Objects.requireNonNull(verificationUri, "verificationUri must not be null");

        if (!ALLOWED_VERSIONS.contains(version)) {
            throw new IllegalArgumentException("Unsupported or forbidden QR protocol version: " + version);
        }
        if (capabilityToken.length() < 32 || capabilityToken.length() > 64) {
            throw new IllegalArgumentException("Invalid capabilityToken length: " + capabilityToken.length());
        }
    }

    /**
     * Serializes this payload into canonical UTF-8 JSON.
     */
    public String toCanonicalJson(ObjectMapper objectMapper) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(this);
            if (bytes.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalStateException("Serialized QR payload exceeds size limit of " + MAX_PAYLOAD_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize canonical QR payload", e);
        }
    }

    /**
     * Parses and strictly validates a canonical QR payload from raw string input.
     */
    public static QrVerificationPayload fromJson(String rawJson, ObjectMapper objectMapper) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("QR payload cannot be blank or null");
        }
        byte[] rawBytes = rawJson.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("QR payload exceeds maximum size limit of " + MAX_PAYLOAD_BYTES + " bytes");
        }
        try {
            QrVerificationPayload payload = objectMapper.readValue(rawBytes, QrVerificationPayload.class);
            if (!ALLOWED_VERSIONS.contains(payload.version())) {
                throw new IllegalArgumentException("Unsupported QR protocol version: " + payload.version());
            }
            return payload;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed or unparseable QR verification payload: " + e.getMessage(), e);
        }
    }
}

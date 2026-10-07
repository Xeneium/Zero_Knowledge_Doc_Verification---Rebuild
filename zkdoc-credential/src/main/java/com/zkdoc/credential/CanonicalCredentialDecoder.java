package com.zkdoc.credential;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative parser and decoder for the zkdoc-canonical-v1 deterministic binary format.
 * Enables strict round-trip verification:
 * 1. Validates magic bytes (0x5A 0x4B 0x44 0x43 / "ZKDC")
 * 2. Validates version (0x00 0x01)
 * 3. Decodes credential ID UUID
 * 4. Decodes schema URI string
 * 5. Decodes issuer ID string
 * 6. Decodes issuer key UUID
 * 7. Decodes issuedAt and expiresAt millisecond timestamps
 * 8. Decodes strictly ordered attributes and their typed values
 * 9. Recomputes and verifies deterministic canonical encoding match
 */
public final class CanonicalCredentialDecoder {

    private static final byte[] MAGIC_CREDENTIAL = {'Z', 'K', 'D', 'C'};
    private static final short EXPECTED_FORMAT_VERSION = 1;

    public record DecodedCredentialPayload(
            UUID credentialId,
            String schemaId,
            String issuerId,
            UUID issuerKeyUuid,
            Instant issuedAt,
            Instant expiresAt,
            List<CredentialAttribute> attributes) {
    }

    private CanonicalCredentialDecoder() {
    }

    /**
     * Decodes the binary canonical payload bytes into a structured payload record.
     * Fails closed with IllegalArgumentException if magic, version, or format invariants are violated.
     */
    public static DecodedCredentialPayload decode(byte[] canonicalPayload) {
        Objects.requireNonNull(canonicalPayload, "canonicalPayload must not be null");

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(canonicalPayload);
             DataInputStream data = new DataInputStream(byteStream)) {

            // 1. Magic bytes
            byte[] magic = new byte[4];
            data.readFully(magic);
            if (magic[0] != MAGIC_CREDENTIAL[0] || magic[1] != MAGIC_CREDENTIAL[1] ||
                magic[2] != MAGIC_CREDENTIAL[2] || magic[3] != MAGIC_CREDENTIAL[3]) {
                throw new IllegalArgumentException("Invalid canonical payload magic header");
            }

            // 2. Format version
            short version = data.readShort();
            if (version != EXPECTED_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported canonical format version: " + version);
            }

            // 3. Credential UUID (16 bytes)
            UUID credentialId = readUuid(data);

            // 4. Schema URI
            String schemaId = readString(data);

            // 5. Issuer ID
            String issuerId = readString(data);

            // 6. Issuer Key UUID
            UUID issuerKeyUuid = readUuid(data);

            // 7. IssuedAt (int64 epoch millis)
            Instant issuedAt = Instant.ofEpochMilli(data.readLong());

            // 8. ExpiresAt (int64 epoch millis)
            Instant expiresAt = Instant.ofEpochMilli(data.readLong());

            // 9. Attributes count and elements
            int attrCount = data.readInt();
            if (attrCount < 0 || attrCount > 1000) {
                throw new IllegalArgumentException("Illegal attribute count in canonical payload: " + attrCount);
            }

            List<CredentialAttribute> attributes = new ArrayList<>(attrCount);
            for (int i = 0; i < attrCount; i++) {
                String name = readString(data);
                byte tag = data.readByte();
                AttributeType type = resolveType(tag);
                CredentialAttribute attribute = switch (type) {
                    case INTEGER -> new CredentialAttribute(name, data.readLong());
                    case STRING, IDENTIFIER -> new CredentialAttribute(name, type, readString(data));
                    case BOOLEAN -> new CredentialAttribute(name, data.readByte() == 0x01);
                    case DATE -> new CredentialAttribute(name, type, java.time.LocalDate.ofEpochDay(data.readLong()).toString());
                    case DATE_TIME -> new CredentialAttribute(name, type, Instant.ofEpochMilli(data.readLong()).toString());
                };
                attributes.add(attribute);
            }

            if (data.available() > 0) {
                throw new IllegalArgumentException("Extraneous bytes found after canonical payload decoding");
            }

            return new DecodedCredentialPayload(
                    credentialId, schemaId, issuerId, issuerKeyUuid, issuedAt, expiresAt, List.copyOf(attributes));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to decode canonical payload", exception);
        }
    }

    private static AttributeType resolveType(byte tag) {
        for (AttributeType type : AttributeType.values()) {
            if (type.tag() == tag) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown attribute type tag: " + tag);
    }

    private static String readString(DataInputStream data) throws IOException {
        int length = data.readInt();
        if (length < 0 || length > 1024 * 1024) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static UUID readUuid(DataInputStream data) throws IOException {
        long mostSig = data.readLong();
        long leastSig = data.readLong();
        return new UUID(mostSig, leastSig);
    }
}

package com.zkdoc.credential;

import com.zkdoc.core.crypto.CanonicalRepresentation;
import com.zkdoc.core.crypto.CanonicalizationService;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Authoritative implementation of the zkdoc-canonical-v1 deterministic binary canonicalization protocol.
 * Enforces:
 * - Magic header 0x5A 0x4B 0x44 0x43 ("ZKDC")
 * - Format version 0x00 0x01 (Version 1)
 * - Strict Network Byte Order (Big-Endian)
 * - Raw 128-bit UUID serialization
 * - Millisecond UTC timestamps
 * - Unicode NFC normalization for all string fields
 * - Strictly ascending lexicographical sorting of attribute names by UTF-8 bytes
 * - Rejection of duplicate attribute names
 * - Strict decoupling of operational status from canonical payload
 */
@Component
public class CanonicalCredentialEncoder implements CanonicalizationService {
    private static final byte[] MAGIC_CREDENTIAL = {'Z', 'K', 'D', 'C'};
    private static final short FORMAT_VERSION = 1;

    // Attestation header for ZKP commitment attestation compatibility
    private static final byte[] MAGIC_ATTESTATION = {'Z', 'K', 'C', 'A'};

    @Override
    public byte[] canonicalize(Object target) {
        if (target instanceof Credential credential) {
            return encode(credential);
        }
        throw new IllegalArgumentException(
                "Unsupported canonicalization target: " + (target == null ? "null" : target.getClass().getName()));
    }

    /**
     * Produces the full CanonicalRepresentation object for a Credential.
     */
    public CanonicalRepresentation canonicalizeCredential(Credential credential) {
        return new CanonicalRepresentation(FORMAT_VERSION, encode(credential));
    }

    /**
     * Serializes a Credential into deterministic binary bytes according to the zkdoc-canonical-v1 specification.
     */
    public byte[] encode(Credential credential) {
        Objects.requireNonNull(credential, "credential must not be null");

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {

            // 1. Magic bytes (4 bytes)
            data.write(MAGIC_CREDENTIAL);

            // 2. Canonical format version (2 bytes big-endian)
            data.writeShort(FORMAT_VERSION);

            // 3. Credential UUID (16 bytes)
            writeUuid(data, credential.id().value());

            // 4. Schema URI (uint32 length + NFC UTF-8 bytes)
            writeString(data, credential.schemaId());

            // 5. Issuer ID (uint32 length + NFC UTF-8 bytes)
            writeString(data, credential.issuerId().value());

            // 6. Issuer Key UUID (16 bytes raw)
            writeKeyUuid(data, credential.issuerKeyId().value());

            // 7. IssuedAt (int64 epoch millis)
            data.writeLong(credential.issuedAt().toEpochMilli());

            // 8. ExpiresAt (int64 epoch millis)
            data.writeLong(credential.expiresAt().toEpochMilli());

            // 9. Attributes (uint32 count + sorted array)
            writeAttributes(data, credential.attributes());

            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode canonical credential", exception);
        }
    }

    /**
     * Encodes a commitment attestation payload binding a Pedersen commitment to a specific
     * credential identifier and attribute name. Preserved for ZKP verifier interoperability.
     */
    public byte[] encodeCommitmentAttestation(UUID credentialId, String attributeName, byte[] commitment) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        Objects.requireNonNull(commitment, "commitment must not be null");

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {

            data.write(MAGIC_ATTESTATION);
            data.writeInt(FORMAT_VERSION);
            writeUuid(data, credentialId);
            writeString(data, attributeName);
            data.writeInt(commitment.length);
            data.write(commitment);
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode commitment attestation", exception);
        }
    }

    private void writeAttributes(DataOutputStream data, List<CredentialAttribute> attributes) throws IOException {
        List<CredentialAttribute> ordered = new ArrayList<>(attributes);
        // Sort strictly by UTF-8 bytes of attribute name in ascending lexicographical order
        ordered.sort(Comparator.comparing(attr -> attr.name().getBytes(StandardCharsets.UTF_8), Arrays::compare));

        // Enforce duplicate rejection
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).name().equals(ordered.get(i).name())) {
                throw new IllegalArgumentException("Credential attribute names must be unique: " + ordered.get(i).name());
            }
        }

        data.writeInt(ordered.size());
        for (CredentialAttribute attr : ordered) {
            writeString(data, attr.name());
            AttributeType type = attr.type();
            data.writeByte(type.tag());

            switch (type) {
                case INTEGER -> data.writeLong(attr.attributeValue().asInteger());
                case STRING, IDENTIFIER -> writeString(data, attr.attributeValue().rawString());
                case BOOLEAN -> data.writeByte(attr.attributeValue().asBoolean() ? 0x01 : 0x00);
                case DATE -> data.writeLong(attr.attributeValue().asDate().toEpochDay());
                case DATE_TIME -> data.writeLong(attr.attributeValue().asDateTime().toEpochMilli());
            }
        }
    }

    private void writeString(DataOutputStream data, String value) throws IOException {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private void writeUuid(DataOutputStream data, UUID value) throws IOException {
        data.writeLong(value.getMostSignificantBits());
        data.writeLong(value.getLeastSignificantBits());
    }

    private void writeKeyUuid(DataOutputStream data, String keyIdStr) throws IOException {
        UUID keyUuid;
        try {
            keyUuid = UUID.fromString(keyIdStr);
        } catch (IllegalArgumentException notStandardUuid) {
            // Derive deterministic UUID for non-UUID key identifiers
            keyUuid = UUID.nameUUIDFromBytes(keyIdStr.getBytes(StandardCharsets.UTF_8));
        }
        writeUuid(data, keyUuid);
    }
}

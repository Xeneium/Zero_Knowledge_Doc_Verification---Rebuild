package com.zkdoc.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalCredentialDecoderTest {

    private final CanonicalCredentialEncoder encoder = new CanonicalCredentialEncoder();

    @Test
    @DisplayName("Round-trip encoding and decoding preserves all claims exactly")
    void roundTripEncodingAndDecoding() {
        UUID credId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(3600);

        Credential credential = CredentialDraft.newDraft()
                .id(credId)
                .schemaId("urn:zkdoc:schema:kyc:v1")
                .issuerId("urn:zkdoc:issuer:gov-dept")
                .issuerKeyId("key-2026-prod-1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .attribute(new CredentialAttribute("age", 25L))
                .attribute(new CredentialAttribute("citizen", true))
                .attribute(new CredentialAttribute("country", "US"))
                .build();

        byte[] canonicalBytes = encoder.encode(credential);
        assertNotNull(canonicalBytes);

        CanonicalCredentialDecoder.DecodedCredentialPayload decoded =
                CanonicalCredentialDecoder.decode(canonicalBytes);

        assertEquals(credId, decoded.credentialId());
        assertEquals("urn:zkdoc:schema:kyc:v1", decoded.schemaId());
        assertEquals("urn:zkdoc:issuer:gov-dept", decoded.issuerId());
        assertEquals(issuedAt.toEpochMilli(), decoded.issuedAt().toEpochMilli());
        assertEquals(expiresAt.toEpochMilli(), decoded.expiresAt().toEpochMilli());
        assertEquals(3, decoded.attributes().size());

        // Attributes are decoded in sorted order (age, citizen, country)
        assertEquals("age", decoded.attributes().get(0).name());
        assertEquals(25L, decoded.attributes().get(0).attributeValue().asInteger());
        assertEquals("citizen", decoded.attributes().get(1).name());
        assertTrue(decoded.attributes().get(1).attributeValue().asBoolean());
        assertEquals("country", decoded.attributes().get(2).name());
        assertEquals("US", decoded.attributes().get(2).attributeValue().rawString());
    }

    @Test
    @DisplayName("Decoder rejects corrupt magic header")
    void rejectsCorruptMagicHeader() {
        byte[] invalid = new byte[] { 'B', 'A', 'D', 'C', 0, 1 };
        assertThrows(IllegalArgumentException.class, () -> CanonicalCredentialDecoder.decode(invalid));
    }

    @Test
    @DisplayName("Decoder rejects truncated bytes")
    void rejectsTruncatedBytes() {
        byte[] truncated = new byte[] { 'Z', 'K', 'D', 'C' };
        assertThrows(IllegalArgumentException.class, () -> CanonicalCredentialDecoder.decode(truncated));
    }
}

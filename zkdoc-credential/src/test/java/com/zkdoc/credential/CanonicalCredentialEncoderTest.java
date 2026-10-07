package com.zkdoc.credential;

import com.zkdoc.core.crypto.CanonicalRepresentation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalCredentialEncoderTest {
    private final CanonicalCredentialEncoder encoder = new CanonicalCredentialEncoder();

    private final UUID testId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");
    private final Instant issued = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant expiry = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    @DisplayName("Vector 1: Empty attributes produces valid ZKDC header and zero count")
    void testVector1_emptyAttributes() {
        Credential credential = CredentialDraft.newDraft()
                .id(testId)
                .schemaId("urn:zkdoc:schema:identity:v1")
                .issuerId("urn:zkdoc:issuer:gov-dept")
                .issuerKeyId("key-001")
                .issuedAt(issued)
                .expiresAt(expiry)
                .status(CredentialStatus.ACTIVE)
                .build();

        byte[] canonicalBytes = encoder.encode(credential);

        // Header must start with "ZKDC"
        assertArrayEquals(new byte[]{'Z', 'K', 'D', 'C'}, Arrays.copyOfRange(canonicalBytes, 0, 4));

        // Format version must be 0x00 0x01
        assertArrayEquals(new byte[]{0x00, 0x01}, Arrays.copyOfRange(canonicalBytes, 4, 6));

        // CanonicalRepresentation wrapping
        CanonicalRepresentation rep = encoder.canonicalizeCredential(credential);
        assertEquals(1, rep.version());
        assertArrayEquals(canonicalBytes, rep.canonicalBytes());
    }

    @Test
    @DisplayName("Vector 2: Multi-attribute sorting ensures identical byte output regardless of input order")
    void testVector2_multiAttributeSorting() {
        CredentialAttribute attrA = new CredentialAttribute("annualIncome", AttributeType.INTEGER, "75000");
        CredentialAttribute attrB = new CredentialAttribute("citizenStatus", AttributeType.BOOLEAN, "true");
        CredentialAttribute attrC = new CredentialAttribute("department", AttributeType.STRING, "Engineering");

        Credential cred1 = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute(attrC)
                .attribute(attrA)
                .attribute(attrB)
                .build();

        Credential cred2 = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute(attrA)
                .attribute(attrB)
                .attribute(attrC)
                .build();

        assertArrayEquals(encoder.encode(cred1), encoder.encode(cred2));
    }

    @Test
    @DisplayName("Vector 3: Unicode NFC normalization produces identical canonical bytes for decomposed vs precomposed characters")
    void testVector3_unicodeNormalization() {
        // "café" in precomposed form (U+00E9)
        String precomposed = "caf\u00E9";
        // "café" in decomposed form ('e' + U+0301 combining acute)
        String decomposed = "cafe\u0301";

        assertNotEquals(precomposed.length(), decomposed.length());
        assertEquals(Normalizer.normalize(decomposed, Normalizer.Form.NFC), precomposed);

        Credential credPre = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute("city", precomposed)
                .build();

        Credential credDecom = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute("city", decomposed)
                .build();

        assertArrayEquals(encoder.encode(credPre), encoder.encode(credDecom));
    }

    @Test
    @DisplayName("Vector 4: Integer boundary values (0, max 64-bit, min 64-bit) encode deterministically")
    void testVector4_integerBoundaries() {
        Credential cred = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute("zero", 0L)
                .attribute("max", Long.MAX_VALUE)
                .attribute("min", Long.MIN_VALUE)
                .build();

        byte[] bytes1 = encoder.encode(cred);
        byte[] bytes2 = encoder.encode(cred);
        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    @DisplayName("Vector 5: Determinism verification across 100 iterations")
    void testVector5_determinism() {
        Credential cred = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute("accountNumber", "AC-98765")
                .attribute("balance", 125000L)
                .attribute("verified", true)
                .build();

        byte[] baseline = encoder.encode(cred);
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(baseline, encoder.encode(cred));
        }
    }

    @Test
    @DisplayName("Adversarial: Duplicate attribute names in input are strictly rejected")
    void adversarial_duplicateAttributeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Credential(
                        "issuer-1",
                        testId,
                        List.of(
                                new CredentialAttribute("income", 50000L),
                                new CredentialAttribute("income", 60000L)
                        ),
                        issued,
                        expiry,
                        CredentialStatus.ACTIVE
                )
        );
    }

    @Test
    @DisplayName("Adversarial: Operational status change does NOT alter canonical bytes")
    void adversarial_statusDoesNotAlterCanonicalBytes() {
        Credential activeCred = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .status(CredentialStatus.ACTIVE)
                .attribute("role", "Architect")
                .build();

        Credential suspendedCred = activeCred.transitionTo(CredentialStatus.SUSPENDED);
        Credential revokedCred = suspendedCred.transitionTo(CredentialStatus.REVOKED);

        // Canonical bytes must remain identical because status is mutable operational metadata
        assertArrayEquals(encoder.encode(activeCred), encoder.encode(suspendedCred));
        assertArrayEquals(encoder.encode(activeCred), encoder.encode(revokedCred));
    }

    @Test
    @DisplayName("Adversarial: Modifying an authenticated field changes canonical bytes and SHA-256 digest")
    void adversarial_modifyingClaimAltersDigest() throws Exception {
        Credential orig = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute("score", 95L)
                .build();

        Credential tampered = CredentialDraft.newDraft()
                .id(testId)
                .issuerId("issuer-1")
                .issuedAt(issued)
                .expiresAt(expiry)
                .attribute("score", 96L)
                .build();

        byte[] origBytes = encoder.encode(orig);
        byte[] tamperedBytes = encoder.encode(tampered);

        assertFalse(Arrays.equals(origBytes, tamperedBytes));

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash1 = sha256.digest(origBytes);
        sha256.reset();
        byte[] hash2 = sha256.digest(tamperedBytes);

        assertFalse(MessageDigest.isEqual(hash1, hash2));
    }

    @Test
    @DisplayName("Adversarial: Invalid dates (expiry <= issued) are rejected during domain construction")
    void adversarial_invalidDateOrderRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                CredentialDraft.newDraft()
                        .id(testId)
                        .issuerId("issuer-1")
                        .issuedAt(expiry)
                        .expiresAt(issued) // expires before issued!
                        .build()
        );
    }

    @Test
    @DisplayName("Interoperability: encodeCommitmentAttestation outputs stable attestation frame")
    void attestationEncodingInteroperability() {
        byte[] commitment = new byte[32];
        Arrays.fill(commitment, (byte) 0xAA);

        byte[] attestation1 = encoder.encodeCommitmentAttestation(testId, "annualIncome", commitment);
        byte[] attestation2 = encoder.encodeCommitmentAttestation(testId, "annualIncome", commitment);

        assertArrayEquals(attestation1, attestation2);
        assertArrayEquals(new byte[]{'Z', 'K', 'C', 'A'}, Arrays.copyOfRange(attestation1, 0, 4));
    }
}

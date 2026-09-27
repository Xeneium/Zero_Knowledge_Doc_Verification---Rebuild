package com.zkdoc.credential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalCredentialEncoderTest {
    private final CanonicalCredentialEncoder encoder = new CanonicalCredentialEncoder();

    @Test
    void encodesAttributesInFixedNameOrderRegardlessOfInputListOrder() {
        UUID id = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiry = Instant.parse("2027-01-01T00:00:00Z");
        Credential first = new Credential("issuer-a", id,
                List.of(new CredentialAttribute("name", "Asha"), new CredentialAttribute("course", "ZKP")),
                issued, expiry, CredentialStatus.ACTIVE);
        Credential reordered = new Credential("issuer-a", id,
                List.of(new CredentialAttribute("course", "ZKP"), new CredentialAttribute("name", "Asha")),
                issued, expiry, CredentialStatus.ACTIVE);

        assertArrayEquals(encoder.encode(first), encoder.encode(reordered));
    }

    @Test
    void changesEncodingWhenAValueChanges() {
        UUID id = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiry = Instant.parse("2027-01-01T00:00:00Z");
        Credential first = new Credential("issuer-a", id, List.of(new CredentialAttribute("name", "Asha")),
                issued, expiry, CredentialStatus.ACTIVE);
        Credential modified = new Credential("issuer-a", id, List.of(new CredentialAttribute("name", "Anu")),
                issued, expiry, CredentialStatus.ACTIVE);

        assertFalse(java.util.Arrays.equals(encoder.encode(first), encoder.encode(modified)));
    }
}

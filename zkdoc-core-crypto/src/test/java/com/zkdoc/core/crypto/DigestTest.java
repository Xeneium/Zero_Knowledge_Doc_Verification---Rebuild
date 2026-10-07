package com.zkdoc.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class DigestTest {

    @Test
    @DisplayName("Digest creation succeeds with exact 32 bytes and rejects incorrect length")
    void validatesExact32Bytes() {
        byte[] valid = new byte[32];
        valid[0] = 0x12;
        valid[31] = 0x34;

        Digest digest = Digest.of(valid);
        assertArrayEquals(valid, digest.getBytes());

        // Defensive copying check
        valid[0] = 0x00;
        assertNotEquals(0x00, digest.getBytes()[0]);

        assertThrows(NullPointerException.class, () -> Digest.of(null));
        assertThrows(IllegalArgumentException.class, () -> Digest.of(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> Digest.of(new byte[33]));
    }

    @Test
    @DisplayName("Digest converts to and from lowercase hexadecimal representation")
    void hexConversion() {
        String hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        Digest digest = Digest.fromHex(hex);

        assertEquals(hex, digest.toHex());
        assertTrue(digest.matches(HexFormat.of().parseHex(hex)));
        assertFalse(digest.matches(new byte[32]));

        assertThrows(IllegalArgumentException.class, () -> Digest.fromHex("short"));
    }

    @Test
    @DisplayName("CanonicalRepresentation preserves format version and provides defensive copies")
    void canonicalRepresentation() {
        byte[] payload = new byte[]{1, 2, 3, 4};
        CanonicalRepresentation rep = new CanonicalRepresentation(1, payload);

        assertEquals(1, rep.version());
        assertEquals(4, rep.length());
        assertArrayEquals(payload, rep.canonicalBytes());

        payload[0] = 99;
        assertEquals(1, rep.canonicalBytes()[0]);

        assertThrows(IllegalArgumentException.class, () -> new CanonicalRepresentation(0, payload));
    }
}

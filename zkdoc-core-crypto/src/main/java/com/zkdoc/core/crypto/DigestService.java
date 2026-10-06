package com.zkdoc.core.crypto;

import java.io.IOException;
import java.io.InputStream;

/**
 * Contract for deterministic cryptographic hashing (SHA-256).
 * Implementations must provide thread-safe, side-effect-free byte stream hashing.
 */
public interface DigestService {

    /**
     * Computes the 32-byte SHA-256 digest of the given binary payload.
     *
     * @param data Raw input bytes
     * @return 32-byte cryptographic hash
     */
    byte[] sha256(byte[] data);

    /**
     * Computes the 32-byte SHA-256 digest by streaming directly from an input stream
     * without buffering the complete stream in memory.
     *
     * @param inputStream Source binary stream
     * @return 32-byte cryptographic hash
     * @throws IOException on I/O read failure
     */
    byte[] sha256(InputStream inputStream) throws IOException;

    /**
     * Computes the SHA-256 digest and formats it as a 64-character lowercase hex string.
     *
     * @param data Raw input bytes
     * @return 64-character lowercase hexadecimal string
     */
    String sha256Hex(byte[] data);
}

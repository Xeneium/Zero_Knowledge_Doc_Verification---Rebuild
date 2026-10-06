package com.zkdoc.core.crypto;

/**
 * Contract for deterministic, reproducible binary or JSON canonicalization of domain entities
 * prior to cryptographic signing or hashing.
 */
public interface CanonicalizationService {

    /**
     * Encodes the domain object into a deterministic, platform-independent canonical byte sequence.
     *
     * @param target Entity to canonicalize
     * @return Deterministic binary byte representation
     * @throws IllegalArgumentException if the object contains unsupported or non-canonical types
     */
    byte[] canonicalize(Object target);
}

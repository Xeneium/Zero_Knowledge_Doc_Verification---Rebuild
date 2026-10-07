package com.zkdoc.zkp;

import java.util.Objects;

/**
 * Result of creating an authenticated attribute commitment.
 * Contains both the public commitment record (which can be safely stored)
 * and the ephemeral holder secret opening material (which is returned once to the caller).
 */
public record AttributeCommitmentResult(
        AttributeCommitment commitment,
        AttributeOpeningSecret openingSecret) {

    public AttributeCommitmentResult {
        Objects.requireNonNull(commitment, "commitment must not be null");
        Objects.requireNonNull(openingSecret, "openingSecret must not be null");
    }
}

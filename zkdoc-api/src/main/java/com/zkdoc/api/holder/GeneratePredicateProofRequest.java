package com.zkdoc.api.holder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Holder request to generate a zero-knowledge predicate proof.
 * <p>
 * Contains solely holder-controlled parameters:
 * - credential reference (via holderCredentialId)
 * - attribute reference (attributeName)
 * - predicate operator (defaults to ">=" if omitted or specified)
 * - threshold (must be non-negative)
 * - transcript nonce (hex string provided by verifier challenge or holder context)
 * <p>
 * NEVER accepts raw attributes, blinding factors, commitments, or attestations.
 */
public record GeneratePredicateProofRequest(
        @NotNull(message = "holderCredentialId must not be null")
        UUID holderCredentialId,

        @NotBlank(message = "attributeName must not be blank")
        @Size(max = 128, message = "attributeName must not exceed 128 characters")
        String attributeName,

        @Pattern(regexp = "^(>=|GREATER_THAN_OR_EQUAL)?$", message = "Unsupported predicate operator")
        String operator,

        @PositiveOrZero(message = "threshold must be non-negative")
        long threshold,

        @NotBlank(message = "nonceHex must not be blank")
        @Pattern(regexp = "^[0-9a-fA-F]{2,128}$", message = "nonceHex must be valid hex between 1 and 64 bytes")
        String nonceHex
) {}

package com.zkdoc.api.holder;

import java.util.UUID;

/**
 * Holder request to generate a zero-knowledge predicate proof.
 * <p>
 * Contains solely holder-controlled parameters:
 * - credential reference (via holderCredentialId)
 * - attribute reference (attributeName)
 * - predicate operator (defaults to ">=" if omitted or specified)
 * - threshold (must be non-negative)
 * - transcript nonce (hex or base64 string provided by verifier challenge or holder context)
 * <p>
 * NEVER accepts raw attributes, blinding factors, commitments, or attestations.
 */
public record GeneratePredicateProofRequest(
        UUID holderCredentialId,
        String attributeName,
        String operator,
        long threshold,
        String nonceHex
) {}

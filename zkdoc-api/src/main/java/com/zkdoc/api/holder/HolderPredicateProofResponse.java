package com.zkdoc.api.holder;

import java.util.UUID;

/**
 * Public response DTO returned to the holder upon successful predicate proof generation.
 * <p>
 * Contains ONLY public statement anchors and cryptographic proof artifact.
 * <p>
 * ABSOLUTELY ZERO SENSITIVE MATERIAL:
 * No raw attribute values, no blinding factors, no witness structures.
 */
public record HolderPredicateProofResponse(
        UUID holderCredentialId,
        UUID credentialId,
        String credentialDigestHex,
        String attributeName,
        String operator,
        long threshold,
        String commitmentPointHex,
        String attestationSignatureHex,
        String attestationKeyId,
        String nonceHex,
        String proofVersion,
        int bitSize,
        String proofBytesHex
) {}

package com.zkdoc.api.holder;

import java.time.Instant;
import java.util.UUID;

/**
 * Public/authenticated metadata response for a holder credential.
 * Intentionally contains ZERO private proving material, blinding factors, or raw witnesses.
 */
public record HolderCredentialResponse(
        UUID id,
        String holderId,
        UUID credentialId,
        String credentialDigestHex,
        String status,
        int version,
        Instant provisionedAt,
        Instant updatedAt) {
}

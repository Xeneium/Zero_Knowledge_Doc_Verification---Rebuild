package com.zkdoc.keys;

import java.util.UUID;

/** Registry metadata; private-key material is never stored in this table. */
public record RegisteredKey(
        UUID id,
        String keyId,
        KeyPurpose purpose,
        KeyStatus status,
        String algorithm,
        String publicKeyPem,
        String privateKeyFileEnvironment) {
}

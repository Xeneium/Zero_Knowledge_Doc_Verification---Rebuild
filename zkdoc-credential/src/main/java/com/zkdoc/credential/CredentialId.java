package com.zkdoc.credential;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a 128-bit globally unique credential identifier.
 */
public record CredentialId(UUID value) {
    public CredentialId {
        Objects.requireNonNull(value, "CredentialId UUID must not be null");
    }

    public static CredentialId of(UUID value) {
        return new CredentialId(value);
    }

    public static CredentialId fromString(String uuidString) {
        Objects.requireNonNull(uuidString, "uuidString must not be null");
        return new CredentialId(UUID.fromString(uuidString.trim()));
    }

    public static CredentialId random() {
        return new CredentialId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

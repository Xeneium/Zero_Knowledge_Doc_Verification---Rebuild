package com.zkdoc.credential;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a 128-bit globally unique holder credential identifier.
 */
public record HolderCredentialId(UUID value) {
    public HolderCredentialId {
        Objects.requireNonNull(value, "HolderCredentialId UUID must not be null");
    }

    public static HolderCredentialId of(UUID value) {
        return new HolderCredentialId(value);
    }

    public static HolderCredentialId fromString(String uuidString) {
        Objects.requireNonNull(uuidString, "uuidString must not be null");
        return new HolderCredentialId(UUID.fromString(uuidString.trim()));
    }

    public static HolderCredentialId random() {
        return new HolderCredentialId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

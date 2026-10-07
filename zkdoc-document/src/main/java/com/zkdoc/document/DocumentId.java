package com.zkdoc.document;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed value object representing a unique document identifier.
 */
public record DocumentId(UUID value) {
    public DocumentId {
        Objects.requireNonNull(value, "DocumentId value must not be null");
    }

    public static DocumentId of(UUID value) {
        return new DocumentId(value);
    }

    public static DocumentId random() {
        return new DocumentId(UUID.randomUUID());
    }

    public static DocumentId fromString(String uuidString) {
        return new DocumentId(UUID.fromString(Objects.requireNonNull(uuidString).trim()));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

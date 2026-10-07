package com.zkdoc.document;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed value object representing a distinct document artifact identifier.
 */
public record ArtifactId(UUID value) {
    public ArtifactId {
        Objects.requireNonNull(value, "ArtifactId value must not be null");
    }

    public static ArtifactId of(UUID value) {
        return new ArtifactId(value);
    }

    public static ArtifactId random() {
        return new ArtifactId(UUID.randomUUID());
    }

    public static ArtifactId fromString(String uuidString) {
        return new ArtifactId(UUID.fromString(Objects.requireNonNull(uuidString).trim()));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.zkdoc.credential;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a registered cryptographic key identifier.
 */
public record KeyId(String value) {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-.:]{1,64}$");

    public KeyId {
        Objects.requireNonNull(value, "KeyId must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("KeyId must not be blank");
        }
        if (!VALID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("KeyId contains invalid characters or exceeds 64 chars: " + trimmed);
        }
        value = trimmed;
    }

    public static KeyId of(String value) {
        return new KeyId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

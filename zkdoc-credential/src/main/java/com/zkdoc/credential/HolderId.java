package com.zkdoc.credential;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing an authoritative holder subject identifier.
 * Binds to authenticated user subject / principal in the platform identity system.
 */
public record HolderId(String value) {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-.:/@]{1,128}$");

    public HolderId {
        Objects.requireNonNull(value, "HolderId must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("HolderId must not be blank");
        }
        if (!VALID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("HolderId contains invalid characters or exceeds 128 chars: " + trimmed);
        }
        value = trimmed;
    }

    public static HolderId of(String value) {
        return new HolderId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

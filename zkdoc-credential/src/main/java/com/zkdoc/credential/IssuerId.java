package com.zkdoc.credential;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing an authoritative issuer identifier (e.g. URI, URN, or alphanumeric ID).
 */
public record IssuerId(String value) {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-.:/@]{1,128}$");

    public IssuerId {
        Objects.requireNonNull(value, "IssuerId must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("IssuerId must not be blank");
        }
        if (!VALID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("IssuerId contains invalid characters or exceeds 128 chars: " + trimmed);
        }
        value = trimmed;
    }

    public static IssuerId of(String value) {
        return new IssuerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

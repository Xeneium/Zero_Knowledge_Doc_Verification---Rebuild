package com.zkdoc.credential;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a validated, NFC-normalized credential attribute name.
 * Restricted to ASCII alphanumeric, underscores, and hyphens (max 128 characters).
 */
public record AttributeName(String value) implements Comparable<AttributeName> {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{1,128}$");

    public AttributeName {
        Objects.requireNonNull(value, "AttributeName must not be null");
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("AttributeName must not be blank");
        }
        if (!VALID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "AttributeName must be alphanumeric with underscore/hyphen (max 128 chars): " + normalized);
        }
        value = normalized;
    }

    public static AttributeName of(String value) {
        return new AttributeName(value);
    }

    public byte[] utf8Bytes() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int compareTo(AttributeName other) {
        return Arrays.compare(this.utf8Bytes(), other.utf8Bytes());
    }

    @Override
    public String toString() {
        return value;
    }
}

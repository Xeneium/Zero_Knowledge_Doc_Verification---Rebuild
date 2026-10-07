package com.zkdoc.credential;

/**
 * Value object representing the version of the internal credential structure.
 */
public record CredentialVersion(int value) {
    public static final CredentialVersion V1 = new CredentialVersion(1);

    public CredentialVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("CredentialVersion must be positive, got: " + value);
        }
    }

    public static CredentialVersion of(int value) {
        return new CredentialVersion(value);
    }

    @Override
    public String toString() {
        return "v" + value;
    }
}

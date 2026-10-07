package com.zkdoc.credential;

/**
 * Value object representing the version of the canonical binary encoding protocol.
 */
public record CanonicalizationVersion(int value) {
    public static final CanonicalizationVersion V1 = new CanonicalizationVersion(1);

    public CanonicalizationVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("CanonicalizationVersion must be positive, got: " + value);
        }
    }

    public static CanonicalizationVersion of(int value) {
        return new CanonicalizationVersion(value);
    }

    @Override
    public String toString() {
        return "zkdoc-canonical-v" + value;
    }
}

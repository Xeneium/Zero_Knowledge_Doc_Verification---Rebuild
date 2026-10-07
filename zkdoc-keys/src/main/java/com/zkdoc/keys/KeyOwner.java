package com.zkdoc.keys;

import java.util.Objects;

/**
 * Immutable value object defining explicit key ownership and trust domain.
 */
public record KeyOwner(String ownerId, KeyOwnerType ownerType) {

    public KeyOwner {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(ownerType, "ownerType must not be null");
        ownerId = ownerId.trim();
        if (ownerId.isEmpty()) {
            throw new IllegalArgumentException("ownerId must not be empty");
        }
    }

    public static KeyOwner platform(String ownerId) {
        return new KeyOwner(ownerId, KeyOwnerType.PLATFORM);
    }

    public static KeyOwner issuer(String issuerId) {
        return new KeyOwner(issuerId, KeyOwnerType.ISSUER);
    }
}

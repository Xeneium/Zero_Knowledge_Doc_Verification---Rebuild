package com.zkdoc.api.qr;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing the primary key of a VerificationCapability entity.
 */
public record CapabilityId(UUID value) {
    public CapabilityId {
        Objects.requireNonNull(value, "CapabilityId value must not be null");
    }

    public static CapabilityId of(UUID value) {
        return new CapabilityId(value);
    }

    public static CapabilityId random() {
        return new CapabilityId(UUID.randomUUID());
    }

    public static CapabilityId fromString(String uuid) {
        Objects.requireNonNull(uuid, "uuid string must not be null");
        return new CapabilityId(UUID.fromString(uuid.trim()));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

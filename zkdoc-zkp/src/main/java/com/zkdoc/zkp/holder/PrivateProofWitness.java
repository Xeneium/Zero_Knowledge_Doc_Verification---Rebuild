package com.zkdoc.zkp.holder;

import java.util.Arrays;
import java.util.Objects;
import javax.security.auth.Destroyable;

/**
 * Ephemeral private witness containing the plaintext attribute and secret blinding factor.
 * <p>
 * CRITICAL SECURITY INVARIANTS:
 * - Strictly isolated inside the cryptographic proving boundary.
 * - Implements {@link Destroyable} for active memory zeroization immediately after proof creation.
 * - {@link #toString()} explicitly redacts all values.
 * - Never serialized, logged, audited, or sent across any network or controller boundary.
 */
public record PrivateProofWitness(
        long attributeValue,
        byte[] blindingFactor
) implements Destroyable {

    public PrivateProofWitness {
        Objects.requireNonNull(blindingFactor, "blindingFactor must not be null");
        if (blindingFactor.length == 0) {
            throw new IllegalArgumentException("blindingFactor must not be empty");
        }
        if (attributeValue < 0) {
            throw new IllegalArgumentException("Negative attribute value is not supported: " + attributeValue);
        }
        blindingFactor = blindingFactor.clone();
    }

    @Override
    public byte[] blindingFactor() {
        return blindingFactor.clone();
    }

    @Override
    public void destroy() {
        Arrays.fill(blindingFactor, (byte) 0);
    }

    @Override
    public boolean isDestroyed() {
        for (byte b : blindingFactor) {
            if (b != 0) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "PrivateProofWitness{attributeValue=[REDACTED], blindingFactor=[REDACTED]}";
    }
}

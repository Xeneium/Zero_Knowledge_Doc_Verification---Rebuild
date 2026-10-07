package com.zkdoc.zkp;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Secret opening material (blinding factor / random scalar r) for a Pedersen commitment C = vG + rH.
 * <p>
 * CRITICAL SECURITY INVARIANT:
 * This material must NEVER be stored in the database, leaked into logs, audit events, or public URLs.
 * It is ephemeral issuance material delivered strictly to the credential holder for generating range proofs.
 */
public final class AttributeOpeningSecret {

    private final UUID credentialId;
    private final String attributeName;
    private final long attributeValue;
    private final byte[] blindingFactor;
    private final byte[] blindingFingerprint;

    public AttributeOpeningSecret(
            UUID credentialId,
            String attributeName,
            long attributeValue,
            byte[] blindingFactor,
            byte[] blindingFingerprint) {
        this.credentialId = Objects.requireNonNull(credentialId, "credentialId must not be null");
        this.attributeName = Objects.requireNonNull(attributeName, "attributeName must not be null");
        this.attributeValue = attributeValue;
        this.blindingFactor = Objects.requireNonNull(blindingFactor, "blindingFactor must not be null").clone();
        this.blindingFingerprint = Objects.requireNonNull(blindingFingerprint, "blindingFingerprint must not be null").clone();
    }

    public UUID credentialId() {
        return credentialId;
    }

    public String attributeName() {
        return attributeName;
    }

    public long attributeValue() {
        return attributeValue;
    }

    public byte[] blindingFactor() {
        return blindingFactor.clone();
    }

    public byte[] blindingFingerprint() {
        return blindingFingerprint.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeOpeningSecret that)) return false;
        return attributeValue == that.attributeValue
                && credentialId.equals(that.credentialId)
                && attributeName.equals(that.attributeName)
                && Arrays.equals(blindingFactor, that.blindingFactor)
                && Arrays.equals(blindingFingerprint, that.blindingFingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(credentialId, attributeName, attributeValue);
        result = 31 * result + Arrays.hashCode(blindingFactor);
        result = 31 * result + Arrays.hashCode(blindingFingerprint);
        return result;
    }

    @Override
    public String toString() {
        // Explicitly redact blindingFactor from toString()
        return "AttributeOpeningSecret[credentialId=" + credentialId
                + ", attributeName=" + attributeName
                + ", attributeValue=" + attributeValue
                + ", blindingFactor=[REDACTED]"
                + ", blindingFingerprint=" + (blindingFingerprint.length > 0 ? "sha256:present" : "none") + "]";
    }
}

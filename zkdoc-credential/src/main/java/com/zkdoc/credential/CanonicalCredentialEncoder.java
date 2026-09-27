package com.zkdoc.credential;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Version 1 credential encoding: magic bytes, version, issuer, credential ID,
 * sorted attribute pairs, issued time, expiry time, then status.
 */
@Component
public class CanonicalCredentialEncoder {
    private static final byte[] MAGIC = {'Z', 'K', 'C', 'R'};
    private static final int VERSION = 1;

    public byte[] encode(Credential credential) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {
            data.write(MAGIC);
            data.writeInt(VERSION);
            writeString(data, credential.issuerId());
            writeUuid(data, credential.credentialId());
            writeAttributes(data, credential.attributes());
            writeInstant(data, credential.issuedAt());
            writeInstant(data, credential.expiresAt());
            writeString(data, credential.status().name());
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode credential", exception);
        }
    }

    /**
     * Canonical Phase 3 attestation payload. It binds a commitment to exactly
     * one credential identifier and attribute name before the issuer signs it.
     */
    public byte[] encodeCommitmentAttestation(UUID credentialId, String attributeName, byte[] commitment) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {
            data.write(new byte[] {'Z', 'K', 'C', 'A'});
            data.writeInt(VERSION);
            writeUuid(data, credentialId);
            writeString(data, attributeName);
            data.writeInt(commitment.length);
            data.write(commitment);
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode commitment attestation", exception);
        }
    }

    private void writeAttributes(DataOutputStream data, List<CredentialAttribute> attributes) throws IOException {
        List<CredentialAttribute> ordered = new ArrayList<>(attributes);
        ordered.sort(Comparator.comparing(CredentialAttribute::name).thenComparing(CredentialAttribute::value));
        for (int index = 1; index < ordered.size(); index++) {
            if (ordered.get(index - 1).name().equals(ordered.get(index).name())) {
                throw new IllegalArgumentException("Credential attribute names must be unique");
            }
        }
        data.writeInt(ordered.size());
        for (CredentialAttribute attribute : ordered) {
            writeString(data, attribute.name());
            writeString(data, attribute.value());
        }
    }

    private void writeString(DataOutputStream data, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private void writeUuid(DataOutputStream data, java.util.UUID value) throws IOException {
        data.writeLong(value.getMostSignificantBits());
        data.writeLong(value.getLeastSignificantBits());
    }

    private void writeInstant(DataOutputStream data, Instant value) throws IOException {
        data.writeLong(value.getEpochSecond());
        data.writeInt(value.getNano());
    }
}

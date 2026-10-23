package com.zkdoc.credential;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCredentialStore implements CredentialStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCredentialStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(SignedCredential signedCredential) {
        Credential credential = signedCredential.credential();
        jdbcTemplate.update("""
                INSERT INTO credentials (
                    credential_id, issuer_id, issued_at, expires_at, status, canonical_payload, digest, signature, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, credential.credentialId(), credential.issuerIdString(), toTimestamp(credential.issuedAt()), toTimestamp(credential.expiresAt()),
                credential.status().name(), signedCredential.canonicalPayload(), signedCredential.digest(),
                signedCredential.signature());
    }

    @Override
    public Optional<StoredCredential> findById(UUID credentialId) {
        return jdbcTemplate.query("""
                SELECT credential_id, issuer_id, issued_at, expires_at, status, canonical_payload, digest, signature,
                       suspended_at, suspension_reason, revoked_at, revocation_reason, version
                FROM credentials
                WHERE credential_id = ?
                """, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(), credentialId);
    }

    @Override
    public boolean updateLifecycle(
            UUID credentialId,
            CredentialStatus targetStatus,
            int expectedVersion,
            CredentialStatus previousStatus,
            Instant suspendedAt,
            String suspensionReason,
            Instant revokedAt,
            String revocationReason) {

        int updated = jdbcTemplate.update("""
                UPDATE credentials
                SET status = ?,
                    suspended_at = ?,
                    suspension_reason = ?,
                    revoked_at = ?,
                    revocation_reason = ?,
                    version = version + 1
                WHERE credential_id = ? AND version = ?
                """,
                targetStatus.name(),
                toTimestamp(suspendedAt),
                suspensionReason,
                toTimestamp(revokedAt),
                revocationReason,
                credentialId,
                expectedVersion);

        return updated == 1;
    }

    private StoredCredential map(ResultSet rs) throws SQLException {
        String issuerId = getNullableString(rs, "issuer_id");
        if (issuerId == null) {
            issuerId = "system";
        }

        Instant issuedAt = JdbcTypeUtils.parseInstant(rs, "issued_at");
        if (issuedAt == null) {
            issuedAt = Instant.EPOCH;
        }

        Instant expiresAt = JdbcTypeUtils.parseInstant(rs, "expires_at");
        if (expiresAt == null) {
            expiresAt = Instant.EPOCH;
        }
        CredentialStatus status = CredentialStatus.valueOf(rs.getString("status"));

        Instant suspendedAt = JdbcTypeUtils.parseInstant(rs, "suspended_at");
        String suspensionReason = getNullableString(rs, "suspension_reason");

        Instant revokedAt = JdbcTypeUtils.parseInstant(rs, "revoked_at");
        String revocationReason = getNullableString(rs, "revocation_reason");

        int version = 1;
        try {
            version = rs.getInt("version");
            if (version == 0) {
                version = 1;
            }
        } catch (SQLException ignored) {
            // Default to 1 if column not present in older test fixtures
        }

        return new StoredCredential(
                UUID.fromString(rs.getString("credential_id")),
                issuerId,
                issuedAt,
                expiresAt,
                status,
                rs.getBytes("canonical_payload"),
                rs.getBytes("digest"),
                rs.getBytes("signature"),
                suspendedAt,
                suspensionReason,
                revokedAt,
                revocationReason,
                version);
    }

    private static java.sql.Timestamp toTimestamp(Instant instant) {
        return JdbcTypeUtils.toTimestamp(instant);
    }

    private String getNullableString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }
}

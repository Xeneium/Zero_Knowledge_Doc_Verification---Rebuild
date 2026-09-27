package com.zkdoc.credential;

import java.sql.ResultSet;
import java.sql.SQLException;
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
                    credential_id, issuer_id, issued_at, expires_at, status, canonical_payload, digest, signature)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, credential.credentialId().toString(), credential.issuerId(), credential.issuedAt().toString(), credential.expiresAt().toString(),
                credential.status().name(), signedCredential.canonicalPayload(), signedCredential.digest(),
                signedCredential.signature());
    }

    @Override
    public Optional<StoredCredential> findById(UUID credentialId) {
        return jdbcTemplate.query("""
                SELECT credential_id, expires_at, status, canonical_payload, digest, signature
                FROM credentials
                WHERE credential_id = ?
                """, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(), credentialId.toString());
    }

    private StoredCredential map(ResultSet resultSet) throws SQLException {
        return new StoredCredential(
                UUID.fromString(resultSet.getString("credential_id")),
                java.time.Instant.parse(resultSet.getString("expires_at")),
                CredentialStatus.valueOf(resultSet.getString("status")),
                resultSet.getBytes("canonical_payload"),
                resultSet.getBytes("digest"),
                resultSet.getBytes("signature"));
    }
}

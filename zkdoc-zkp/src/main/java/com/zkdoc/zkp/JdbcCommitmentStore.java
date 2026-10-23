package com.zkdoc.zkp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommitmentStore implements CommitmentStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCommitmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean blindingFingerprintExists(byte[] fingerprint) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attribute_commitments WHERE blinding_fingerprint = ?",
                Integer.class, fingerprint);
        return count != null && count > 0;
    }

    @Override
    public void save(AttributeCommitment commitment) {
        jdbcTemplate.update("""
                INSERT INTO attribute_commitments (
                    id, credential_id, attribute_name, commitment, attestation, attestation_payload, blinding_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, commitment.id(), commitment.credentialId(), commitment.attributeName(),
                commitment.commitment(), commitment.attestation(), commitment.attestationPayload(),
                commitment.blindingFingerprint());
    }

    @Override
    public Optional<AttributeCommitment> findLatestByCredentialIdAndAttributeName(
            UUID credentialId, String attributeName) {
        return jdbcTemplate.query("""
                SELECT id, credential_id, attribute_name, commitment, attestation, attestation_payload, blinding_fingerprint
                FROM attribute_commitments
                WHERE credential_id = ? AND attribute_name = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                credentialId, attributeName);
    }

    @Override
    public Optional<AttributeCommitment> findByCredentialIdAndAttributeName(
            UUID credentialId, String attributeName) {
        return findLatestByCredentialIdAndAttributeName(credentialId, attributeName);
    }

    @Override
    public Optional<AttributeCommitment> findById(UUID commitmentId) {
        return jdbcTemplate.query("""
                SELECT id, credential_id, attribute_name, commitment, attestation, attestation_payload, blinding_fingerprint
                FROM attribute_commitments
                WHERE id = ?
                """, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                commitmentId);
    }

    private AttributeCommitment map(ResultSet resultSet) throws SQLException {
        return new AttributeCommitment(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("credential_id")),
                resultSet.getString("attribute_name"),
                resultSet.getBytes("commitment"),
                resultSet.getBytes("attestation"),
                resultSet.getBytes("attestation_payload"),
                resultSet.getBytes("blinding_fingerprint"));
    }
}

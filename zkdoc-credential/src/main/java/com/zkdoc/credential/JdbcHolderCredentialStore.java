package com.zkdoc.credential;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcHolderCredentialStore implements HolderCredentialStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcHolderCredentialStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(HolderCredential holderCredential) {
        jdbcTemplate.update("""
                INSERT INTO holder_credentials (
                    id, holder_id, credential_id, credential_digest, status, version, provisioned_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                holderCredential.id().value().toString(),
                holderCredential.holderId().value(),
                holderCredential.credentialId().value().toString(),
                holderCredential.credentialDigest(),
                holderCredential.status().name(),
                holderCredential.version(),
                holderCredential.provisionedAt().toString(),
                holderCredential.updatedAt().toString());
    }

    @Override
    public Optional<HolderCredential> findById(HolderCredentialId id) {
        return jdbcTemplate.query("""
                SELECT id, holder_id, credential_id, credential_digest, status, version, provisioned_at, updated_at
                FROM holder_credentials
                WHERE id = ?
                """,
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
                id.value().toString());
    }

    @Override
    public Optional<HolderCredential> findByHolderIdAndCredentialId(HolderId holderId, CredentialId credentialId) {
        return jdbcTemplate.query("""
                SELECT id, holder_id, credential_id, credential_digest, status, version, provisioned_at, updated_at
                FROM holder_credentials
                WHERE holder_id = ? AND credential_id = ?
                """,
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
                holderId.value(),
                credentialId.value().toString());
    }

    @Override
    public List<HolderCredential> findAllByHolderId(HolderId holderId) {
        return jdbcTemplate.query("""
                SELECT id, holder_id, credential_id, credential_digest, status, version, provisioned_at, updated_at
                FROM holder_credentials
                WHERE holder_id = ?
                ORDER BY provisioned_at DESC
                """,
                (rs, rowNum) -> map(rs),
                holderId.value());
    }

    @Override
    public void updateStatus(HolderCredentialId id, HolderCredentialStatus status) {
        jdbcTemplate.update("""
                UPDATE holder_credentials
                SET status = ?, version = version + 1, updated_at = ?
                WHERE id = ?
                """,
                status.name(),
                Instant.now().toString(),
                id.value().toString());
    }

    @Override
    public boolean existsByHolderIdAndCredentialId(HolderId holderId, CredentialId credentialId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM holder_credentials
                WHERE holder_id = ? AND credential_id = ?
                """,
                Integer.class,
                holderId.value(),
                credentialId.value().toString());
        return count != null && count > 0;
    }

    private HolderCredential map(ResultSet rs) throws SQLException {
        return new HolderCredential(
                HolderCredentialId.fromString(rs.getString("id")),
                HolderId.of(rs.getString("holder_id")),
                CredentialId.fromString(rs.getString("credential_id")),
                rs.getBytes("credential_digest"),
                HolderCredentialStatus.valueOf(rs.getString("status")),
                rs.getInt("version"),
                Instant.parse(rs.getString("provisioned_at")),
                Instant.parse(rs.getString("updated_at")));
    }
}

package com.zkdoc.keys;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKeyRegistry implements KeyRegistry {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKeyRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose) {
        return jdbcTemplate.query("""
                SELECT id, key_id, purpose, status, algorithm, public_key_pem, private_key_file_environment
                FROM key_registry
                WHERE key_id = ? AND purpose = ? AND status IN ('ACTIVE', 'RETIRED')
                """, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(), keyId, purpose.name());
    }

    @Override
    public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose) {
        return jdbcTemplate.query("""
                SELECT id, key_id, purpose, status, algorithm, public_key_pem, private_key_file_environment
                FROM key_registry
                WHERE purpose = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(), purpose.name());
    }

    @Override
    public void register(RegisteredKey key) {
        jdbcTemplate.update("""
                INSERT INTO key_registry (id, key_id, purpose, status, algorithm, public_key_pem, private_key_file_environment)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, key.id().toString(), key.keyId(), key.purpose().name(), key.status().name(), key.algorithm(),
                key.publicKeyPem(), key.privateKeyFileEnvironment());
    }

    @Override
    public void updateStatus(String keyId, KeyStatus status) {
        String retiredAt = status == KeyStatus.RETIRED ? Instant.now().toString() : null;
        String revokedAt = status == KeyStatus.REVOKED ? Instant.now().toString() : null;
        jdbcTemplate.update("""
                UPDATE key_registry
                SET status = ?, retired_at = ?, revoked_at = ?
                WHERE key_id = ?
                """, status.name(), retiredAt, revokedAt, keyId);
    }

    private RegisteredKey map(ResultSet resultSet) throws SQLException {
        return new RegisteredKey(UUID.fromString(resultSet.getString("id")), resultSet.getString("key_id"),
                KeyPurpose.valueOf(resultSet.getString("purpose")), KeyStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("algorithm"), resultSet.getString("public_key_pem"),
                resultSet.getString("private_key_file_environment"));
    }
}

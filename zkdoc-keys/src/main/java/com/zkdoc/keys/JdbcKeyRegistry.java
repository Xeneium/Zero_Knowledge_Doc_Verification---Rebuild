package com.zkdoc.keys;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence implementation for the public key trust registry.
 * Compatible with both PostgreSQL 16+ and SQLite for local environments.
 */
@Repository
public class JdbcKeyRegistry implements KeyRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final KeyRowMapper rowMapper = new KeyRowMapper();

    public JdbcKeyRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RegisteredKey> findByKeyId(String keyId) {
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                WHERE key_id = ?
                """;
        return jdbcTemplate.query(sql, resultSet -> resultSet.next() ? Optional.of(rowMapper.mapRow(resultSet, 1)) : Optional.empty(), keyId);
    }

    @Override
    public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose) {
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                WHERE key_id = ? AND purpose = ? AND status IN ('ACTIVE', 'RETIRING', 'RETIRED')
                """;
        return jdbcTemplate.query(sql, resultSet -> resultSet.next() ? Optional.of(rowMapper.mapRow(resultSet, 1)) : Optional.empty(), keyId, purpose.name());
    }

    @Override
    public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose, String expectedOwnerId) {
        if (expectedOwnerId == null || expectedOwnerId.isBlank()) {
            return findForVerification(keyId, purpose);
        }
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                WHERE key_id = ? AND purpose = ? AND owner_id = ? AND status IN ('ACTIVE', 'RETIRING', 'RETIRED')
                """;
        return jdbcTemplate.query(sql, resultSet -> resultSet.next() ? Optional.of(rowMapper.mapRow(resultSet, 1)) : Optional.empty(), keyId, purpose.name(), expectedOwnerId);
    }

    @Override
    public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose) {
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                WHERE purpose = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """;
        return jdbcTemplate.query(sql, resultSet -> resultSet.next() ? Optional.of(rowMapper.mapRow(resultSet, 1)) : Optional.empty(), purpose.name());
    }

    @Override
    public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return findActiveForSigning(purpose);
        }
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                WHERE purpose = ? AND owner_id = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """;
        return jdbcTemplate.query(sql, resultSet -> resultSet.next() ? Optional.of(rowMapper.mapRow(resultSet, 1)) : Optional.empty(), purpose.name(), ownerId);
    }

    @Override
    public List<RegisteredKey> findAll() {
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<RegisteredKey> findAllByOwner(String ownerId) {
        String sql = """
                SELECT id, key_id, purpose, status, algorithm, algorithm_parameters,
                       owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                       fingerprint, created_at, activated_at, retired_at, revoked_at,
                       revocation_reason, replacement_key_id, version
                FROM key_registry
                WHERE owner_id = ?
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, rowMapper, ownerId);
    }

    @Override
    public void register(RegisteredKey key) {
        String sql = """
                INSERT INTO key_registry (
                    id, key_id, purpose, status, algorithm, algorithm_parameters,
                    owner_id, owner_type, public_key_pem, public_key_der, public_key_encoding,
                    fingerprint, created_at, activated_at, retired_at, revoked_at,
                    revocation_reason, replacement_key_id, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                key.id(),
                key.keyId(),
                key.purpose().name(),
                key.status().name(),
                key.algorithm(),
                key.algorithmParameters(),
                key.ownerId(),
                key.ownerType().name(),
                key.publicKeyPem(),
                key.publicKeyDer(),
                key.publicKeyEncoding(),
                key.fingerprint(),
                toTimestamp(key.createdAt()),
                toTimestamp(key.activatedAt()),
                toTimestamp(key.retiredAt()),
                toTimestamp(key.revokedAt()),
                key.revocationReason(),
                key.replacementKeyId(),
                key.version());
    }

    @Override
    public void updateStatus(String keyId, KeyStatus status, String revocationReason, String replacementKeyId) {
        Timestamp nowTs = Timestamp.from(Instant.now());
        Timestamp retiredTs = (status == KeyStatus.RETIRED) ? nowTs : null;
        Timestamp revokedTs = (status == KeyStatus.REVOKED) ? nowTs : null;

        String sql = """
                UPDATE key_registry
                SET status = ?,
                    retired_at = COALESCE(?, retired_at),
                    revoked_at = COALESCE(?, revoked_at),
                    revocation_reason = COALESCE(?, revocation_reason),
                    replacement_key_id = COALESCE(?, replacement_key_id),
                    version = version + 1
                WHERE key_id = ?
                """;
        jdbcTemplate.update(sql, status.name(), retiredTs, revokedTs, revocationReason, replacementKeyId, keyId);
    }

    @Override
    public void rotateKey(String oldKeyId, String newKeyId) {
        updateStatus(oldKeyId, KeyStatus.RETIRED, "Key rotated to replacement key: " + newKeyId, newKeyId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class KeyRowMapper implements RowMapper<RegisteredKey> {
        @Override
        public RegisteredKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            String ownerTypeStr = getOptionalString(rs, "owner_type", "PLATFORM");
            KeyOwnerType ownerType;
            try {
                ownerType = KeyOwnerType.valueOf(ownerTypeStr);
            } catch (Exception e) {
                ownerType = KeyOwnerType.PLATFORM;
            }

            return new RegisteredKey(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("key_id"),
                    KeyPurpose.valueOf(rs.getString("purpose")),
                    KeyStatus.valueOf(rs.getString("status")),
                    rs.getString("algorithm"),
                    getOptionalString(rs, "algorithm_parameters", KeyAlgorithmPolicy.MANDATED_PARAMETERS),
                    getOptionalString(rs, "owner_id", "platform"),
                    ownerType,
                    rs.getString("public_key_pem"),
                    rs.getBytes("public_key_der"),
                    getOptionalString(rs, "public_key_encoding", "X.509/PEM"),
                    rs.getString("fingerprint"),
                    parseInstant(rs, "created_at"),
                    parseInstant(rs, "activated_at"),
                    parseInstant(rs, "retired_at"),
                    parseInstant(rs, "revoked_at"),
                    rs.getString("revocation_reason"),
                    rs.getString("replacement_key_id"),
                    getOptionalInt(rs, "version", 1),
                    Collections.emptyMap());
        }

        private static String getOptionalString(ResultSet rs, String column, String defaultValue) {
            try {
                String val = rs.getString(column);
                return val != null ? val : defaultValue;
            } catch (SQLException e) {
                return defaultValue;
            }
        }

        private static int getOptionalInt(ResultSet rs, String column, int defaultValue) {
            try {
                int val = rs.getInt(column);
                return rs.wasNull() ? defaultValue : val;
            } catch (SQLException e) {
                return defaultValue;
            }
        }

        private static Instant parseInstant(ResultSet rs, String column) {
            if (rs == null || column == null) {
                return null;
            }
            try {
                Object obj = rs.getObject(column);
                if (obj instanceof Timestamp ts) {
                    return ts.toInstant();
                }
                if (obj instanceof java.time.OffsetDateTime odt) {
                    return odt.toInstant();
                }
                if (obj instanceof Instant inst) {
                    return inst;
                }
                if (obj instanceof Number num) {
                    return Instant.ofEpochMilli(num.longValue());
                }
            } catch (Exception ignored) {
            }
            try {
                Timestamp ts = rs.getTimestamp(column);
                if (ts != null) {
                    return ts.toInstant();
                }
            } catch (Exception ignored) {
            }
            try {
                String str = rs.getString(column);
                if (str != null && !str.isBlank()) {
                    String trimmed = str.trim();
                    if (trimmed.chars().allMatch(Character::isDigit)) {
                        try {
                            return Instant.ofEpochMilli(Long.parseLong(trimmed));
                        } catch (Exception ignored) {
                        }
                    }
                    String normalized = trimmed.replace(" ", "T");
                    try {
                        return Instant.parse(normalized);
                    } catch (Exception ignored) {
                    }
                    try {
                        return java.time.OffsetDateTime.parse(normalized).toInstant();
                    } catch (Exception ignored) {
                    }
                    try {
                        return java.time.LocalDateTime.parse(normalized).toInstant(java.time.ZoneOffset.UTC);
                    } catch (Exception ignored) {
                    }
                    if (!normalized.endsWith("Z") && !normalized.contains("+")
                            && !normalized.substring(Math.min(10, normalized.length())).contains("-")) {
                        try {
                            return Instant.parse(normalized + "Z");
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}

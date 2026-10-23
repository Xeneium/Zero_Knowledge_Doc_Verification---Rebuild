package com.zkdoc.api.qr;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVerificationCapabilityStore implements VerificationCapabilityStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVerificationCapabilityStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(VerificationCapability capability) {
        jdbcTemplate.update("""
                INSERT INTO verification_capabilities (
                    id, capability_token, credential_id, document_id, mode,
                    protocol_version, status, created_by, created_at, expires_at,
                    revoked_at, revocation_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                capability.id().value(),
                capability.capabilityToken(),
                capability.credentialId() != null ? capability.credentialId() : null,
                capability.documentId() != null ? capability.documentId() : null,
                capability.mode().name(),
                capability.protocolVersion(),
                capability.status().name(),
                capability.createdBy(),
                toTimestamp(capability.createdAt()),
                toTimestamp(capability.expiresAt()),
                capability.revokedAt() != null ? toTimestamp(capability.revokedAt()) : null,
                capability.revocationReason()
        );
    }

    @Override
    public Optional<VerificationCapability> findByToken(String token) {
        return jdbcTemplate.query("""
                SELECT id, capability_token, credential_id, document_id, mode,
                       protocol_version, status, created_by, created_at, expires_at,
                       revoked_at, revocation_reason
                FROM verification_capabilities
                WHERE capability_token = ?
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), token);
    }

    @Override
    public Optional<VerificationCapability> findById(CapabilityId id) {
        return jdbcTemplate.query("""
                SELECT id, capability_token, credential_id, document_id, mode,
                       protocol_version, status, created_by, created_at, expires_at,
                       revoked_at, revocation_reason
                FROM verification_capabilities
                WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), id.value());
    }

    @Override
    public void updateStatus(CapabilityId id, CapabilityStatus status, Instant revokedAt, String reason) {
        jdbcTemplate.update("""
                UPDATE verification_capabilities
                SET status = ?, revoked_at = ?, revocation_reason = ?
                WHERE id = ?
                """,
                status.name(),
                toTimestamp(revokedAt),
                reason,
                id.value()
        );
    }

    private VerificationCapability mapRow(ResultSet rs) throws SQLException {
        String credIdStr = rs.getString("credential_id");
        String docIdStr = rs.getString("document_id");
        String revAtStr = rs.getString("revoked_at");

        return new VerificationCapability(
                CapabilityId.fromString(rs.getString("id")),
                rs.getString("capability_token"),
                credIdStr != null ? UUID.fromString(credIdStr) : null,
                docIdStr != null ? UUID.fromString(docIdStr) : null,
                VerificationMode.valueOf(rs.getString("mode")),
                rs.getString("protocol_version"),
                CapabilityStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"),
                parseInstant(rs, "created_at"),
                parseInstant(rs, "expires_at"),
                parseInstant(rs, "revoked_at"),
                rs.getString("revocation_reason")
        );
    }
    private static java.sql.Timestamp toTimestamp(Instant instant) {
        return instant != null ? java.sql.Timestamp.from(instant) : null;
    }

    private static Instant parseInstant(ResultSet rs, String column) {
        if (rs == null || column == null) {
            return null;
        }
        try {
            Object obj = rs.getObject(column);
            if (obj instanceof java.sql.Timestamp ts) {
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
            java.sql.Timestamp ts = rs.getTimestamp(column);
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
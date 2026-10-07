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
                capability.id().value().toString(),
                capability.capabilityToken(),
                capability.credentialId() != null ? capability.credentialId().toString() : null,
                capability.documentId() != null ? capability.documentId().toString() : null,
                capability.mode().name(),
                capability.protocolVersion(),
                capability.status().name(),
                capability.createdBy(),
                capability.createdAt().toString(),
                capability.expiresAt().toString(),
                capability.revokedAt() != null ? capability.revokedAt().toString() : null,
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
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), id.value().toString());
    }

    @Override
    public void updateStatus(CapabilityId id, CapabilityStatus status, Instant revokedAt, String reason) {
        jdbcTemplate.update("""
                UPDATE verification_capabilities
                SET status = ?, revoked_at = ?, revocation_reason = ?
                WHERE id = ?
                """,
                status.name(),
                revokedAt != null ? revokedAt.toString() : null,
                reason,
                id.value().toString()
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
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("expires_at")),
                revAtStr != null ? Instant.parse(revAtStr) : null,
                rs.getString("revocation_reason")
        );
    }
}
